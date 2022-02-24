package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.application.metrics.ULIK_STARTDATO
import no.nav.syfo.log
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class SyfoSyketilfelleClient(
    private val syketilfelleEndpointURL: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient,
    private val cluster: String
) {

    suspend fun finnStartdato(fnr: String, sykmeldingId: String, fom: LocalDate? = null, tom: LocalDate? = null, sporingsId: UUID): LocalDate {
        val sykeforloep = hentSykeforloep(fnr)
        val aktueltSykeforloep = sykeforloep.firstOrNull {
            it.sykmeldinger.any { simpleSykmelding -> simpleSykmelding.id == sykmeldingId }
        }

        val startdatoFraFomOgTom = if (fom != null && tom != null) {
            finnStartdatoGittFomOgTom(fom, tom, sykeforloep)
        } else {
            null
        }
        if (aktueltSykeforloep == null) {
            log.error("Fant ikke sykeforløp for sykmelding med id $sykmeldingId, {}", sporingsId)
            if (cluster == "dev-fss") {
                log.info("Siden dette er dev setter vi startdato til å være 1 måned siden, {}", sporingsId)
                return LocalDate.now().minusMonths(1)
            }
            throw RuntimeException("Fant ikke sykeforløp for sykmelding med id $sykmeldingId")
        } else {
            if (fom != null && tom != null && aktueltSykeforloep.oppfolgingsdato != startdatoFraFomOgTom) {
                log.warn(
                    "Startdato gitt sykmeldingId: ${formaterDato(aktueltSykeforloep.oppfolgingsdato)}, " +
                        "startdato gitt fom og tom: ${formaterDato(startdatoFraFomOgTom)}, $sporingsId"
                )
                ULIK_STARTDATO.inc()
            }
            return aktueltSykeforloep.oppfolgingsdato
        }
    }

    fun finnStartdatoGittFomOgTom(fom: LocalDate, tom: LocalDate, sykeforloep: List<Sykeforloep>): LocalDate? {
        val aktueltSykeforloep = sykeforloep.filter { it.oppfolgingsdato <= fom }.firstOrNull {
            it.sykmeldinger.any { simpleSykmelding -> tom in simpleSykmelding.fom.rangeTo(simpleSykmelding.tom) }
        }
        return aktueltSykeforloep?.oppfolgingsdato
    }

    suspend fun harSykeforlopMedNyereStartdato(fnr: String, startdato: LocalDate, planlagtMeldingId: UUID): Boolean {
        val sykeforloep = hentSykeforloep(fnr)
        if (sykeforloep.isEmpty()) {
            log.error("Fant ingen sykeforløp for planlagt melding med id $planlagtMeldingId")
            if (cluster == "dev-fss") {
                log.info("Siden dette er dev returnerer vi false, $planlagtMeldingId")
                return false
            }
            throw RuntimeException("Fant ingen sykeforløp for planlagt melding med id $planlagtMeldingId")
        } else {
            return sykeforloep.any { it.oppfolgingsdato.isAfter(startdato) }
        }
    }

    private suspend fun hentSykeforloep(fnr: String): List<Sykeforloep> =
        httpClient.get<List<Sykeforloep>>("$syketilfelleEndpointURL/api/v1/sykeforloep?inkluderPapirsykmelding=true") {
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
                append("fnr", fnr)
            }
        }

    private fun formaterDato(dato: LocalDate?): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        return dato?.format(formatter) ?: "null"
    }
}

data class Sykeforloep(
    var oppfolgingsdato: LocalDate,
    val sykmeldinger: List<SimpleSykmelding>
)

data class SimpleSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate
)
