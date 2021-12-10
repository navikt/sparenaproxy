package no.nav.syfo.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import no.nav.syfo.client.sts.StsOidcClient
import no.nav.syfo.log
import java.time.LocalDate
import java.util.UUID

class SyfoSyketilfelleClient(
    private val syketilfelleEndpointURL: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient,
    private val cluster: String
) {

    suspend fun finnStartdato(aktorId: String, sykmeldingId: String, sporingsId: UUID): LocalDate {
        val sykeforloep = hentSykeforloep(aktorId)
        val aktueltSykeforloep = sykeforloep.firstOrNull {
            it.sykmeldinger.any { simpleSykmelding -> simpleSykmelding.id == sykmeldingId }
        }

        if (aktueltSykeforloep == null) {
            log.error("Fant ikke sykeforløp for sykmelding med id $sykmeldingId, {}", sporingsId)
            if (cluster == "dev-fss") {
                log.info("Siden dette er dev setter vi startdato til å være 1 måned siden, {}", sporingsId)
                return LocalDate.now().minusMonths(1)
            }
            throw RuntimeException("Fant ikke sykeforløp for sykmelding med id $sykmeldingId")
        } else {
            return aktueltSykeforloep.oppfolgingsdato
        }
    }

    suspend fun harSykeforlopMedNyereStartdato(aktorId: String, startdato: LocalDate, planlagtMeldingId: UUID): Boolean {
        val sykeforloep = hentSykeforloep(aktorId)
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

    private suspend fun hentSykeforloep(aktorId: String): List<Sykeforloep> =
        httpClient.get<List<Sykeforloep>>("$syketilfelleEndpointURL/sparenaproxy/$aktorId/sykeforloep") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Authorization", "Bearer ${oidcToken.access_token}")
            }
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
