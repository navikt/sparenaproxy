package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.*
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.log

class SyfoSyketilfelleClient(
    private val syketilfelleEndpointURL: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
    private val cluster: String
) {

    @WithSpan
    suspend fun getStartdato(
        fnr: String,
        fom: LocalDate,
        tom: LocalDate,
        sporingsId: UUID
    ): LocalDate {
        val sykeforloep = fetchSykeforloep(fnr)
        val startdato = getStartdatoByFomTom(fom, tom, sykeforloep)

        if (startdato == null) {
            log.error("Fant ikke startdato for utbetaltEvent med id $sporingsId")
            if (cluster == "dev-gcp") {
                log.info(
                    "Siden dette er dev setter vi startdato til å være 1 måned siden, {}",
                    sporingsId,
                )
                return LocalDate.now().minusMonths(1)
            }
            throw RuntimeException("Fant ikke startdato for utbetaltEvent med id $sporingsId")
        } else {
            return startdato
        }
    }

    @WithSpan
    suspend fun getStartDatoForSykmelding(
        fnr: String,
        sykmeldingId: String,
    ): LocalDate {
        val sykeforloep = fetchSykeforloepUntilSykmeldingFound(fnr, sykmeldingId)
        val aktueltSykeforloep =
            sykeforloep.firstOrNull {
                it.sykmeldinger.any { simpleSykmelding -> simpleSykmelding.id == sykmeldingId }
            }

        if (aktueltSykeforloep == null) {
            log.error(
                "Fant ikke sykeforløp for sykmelding med id $sykmeldingId, sporingsId: {}",
                sykmeldingId,
            )
            if (cluster == "dev-gcp") {
                log.info(
                    "Siden dette er dev setter vi startdato til å være 1 måned siden, sporingsId {}",
                    sykmeldingId,
                )
                return LocalDate.now().minusMonths(1)
            }
            throw RuntimeException("Fant ikke sykeforløp for sykmelding med id $sykmeldingId")
        } else {
            return aktueltSykeforloep.oppfolgingsdato
        }
    }

    @WithSpan
    suspend fun harSykeforlopMedNyereStartdato(
        fnr: String,
        startdato: LocalDate,
        planlagtMeldingId: UUID
    ): Boolean {
        val sykeforloep = fetchSykeforloep(fnr)
        if (sykeforloep.isEmpty()) {
            log.error("Fant ingen sykeforløp for planlagt melding med id $planlagtMeldingId")
            if (cluster == "dev-gcp") {
                log.info("Siden dette er dev returnerer vi false, $planlagtMeldingId")
                return false
            }
            throw RuntimeException(
                "Fant ingen sykeforløp for planlagt melding med id $planlagtMeldingId",
            )
        } else {
            return sykeforloep.any { it.oppfolgingsdato.isAfter(startdato) }
        }
    }

    @WithSpan
    private suspend fun fetchSykeforloepUntilSykmeldingFound(
        fnr: String,
        sykmeldingId: String,
        attempt: Int = 0
    ): List<Sykeforloep> {
        /* We assume that most cases the processing is flex-syketilfelle is pretty fast. But for
        cases where the old 5s implementation actually required 5s, let's back off, so it waits
        at least 5s */
        val retryBackoff = listOf(10, 50, 100, 1000, 1000, 1000, 1000, 1000)

        val response =
            httpClient.get(
                "$syketilfelleEndpointURL/api/v1/sykeforloep?inkluderPapirsykmelding=true"
            ) {
                accept(ContentType.Application.Json)
                val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("fnr", fnr)
                }
            }

        if (response.status != HttpStatusCode.OK) {
            if (attempt > retryBackoff.size) {
                log.error("Retried $attempt times without success")
                throw RuntimeException("Failed to get sykeforloep")
            }

            log.info("Failed to get sykeforloep, retrying in ${retryBackoff[attempt]}ms")
            delay(retryBackoff[attempt].toLong())
            return fetchSykeforloepUntilSykmeldingFound(fnr, sykmeldingId, attempt + 1)
        }

        val result = response.body<List<Sykeforloep>>()
        if (attempt >= retryBackoff.size) {
            log.error("Retried $attempt times without finding sykmelding $sykmeldingId")
            return result
        }

        val relevantSykmelding = result.flatMap { it.sykmeldinger }.find { it.id == sykmeldingId }
        if (relevantSykmelding == null) {
            log.info("Failed to get sykeforloep, retrying in ${retryBackoff[attempt]}ms")
            delay(retryBackoff[attempt].toLong())
            return fetchSykeforloepUntilSykmeldingFound(fnr, sykmeldingId, attempt + 1)
        }

        log.info("Found sykeforloep after $attempt attempts")
        return result
    }

    @WithSpan
    private suspend fun fetchSykeforloep(fnr: String): List<Sykeforloep> =
        httpClient
            .get("$syketilfelleEndpointURL/api/v1/sykeforloep?inkluderPapirsykmelding=true") {
                accept(ContentType.Application.Json)
                val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
                headers {
                    append("Authorization", "Bearer $accessToken")
                    append("fnr", fnr)
                }
            }
            .body<List<Sykeforloep>>()
}

data class Sykeforloep(
    var oppfolgingsdato: LocalDate,
    val sykmeldinger: List<SimpleSykmelding>,
)

data class SimpleSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate,
)

@WithSpan
internal fun getStartdatoByFomTom(
    fom: LocalDate,
    tom: LocalDate,
    sykeforloep: List<Sykeforloep>
): LocalDate? {
    val aktueltSykeforloep =
        sykeforloep
            .sortedByDescending { it.oppfolgingsdato }
            .firstOrNull {
                val forsteFom = it.sykmeldinger.minOf { simpleSykmelding -> simpleSykmelding.fom }
                val sisteTom = it.sykmeldinger.maxOf { simpleSykmelding -> simpleSykmelding.tom }
                val syketilfelleRange = forsteFom.rangeTo(sisteTom)

                it.sykmeldinger.any { fom in syketilfelleRange || tom in syketilfelleRange }
            }
    return aktueltSykeforloep?.oppfolgingsdato
}
