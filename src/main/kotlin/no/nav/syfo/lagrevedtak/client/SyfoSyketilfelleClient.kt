package no.nav.syfo.lagrevedtak.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import no.nav.syfo.client.StsOidcClient

@KtorExperimentalAPI
class SyfoSyketilfelleClient(
    private val syketilfelleEndpointURL: String,
    private val stsClient: StsOidcClient,
    private val httpClient: HttpClient
) {

    suspend fun finnStartdato(aktorId: String, sykmeldingId: String): LocalDate {
        val sykeforloep = hentSykeforloep(aktorId)
        return LocalDate.now()
    }

    suspend fun hentSykeforloep(aktorId: String): List<Sykeforloep> =
        httpClient.get<SyketilfelleRespons>("$syketilfelleEndpointURL/sparenaproxy/$aktorId/sykeforloep") {
            accept(ContentType.Application.Json)
            val oidcToken = stsClient.oidcToken()
            headers {
                append("Authorization", "Bearer ${oidcToken.access_token}")
            }
        }.sykeforloep
}

data class SyketilfelleRespons(
    val sykeforloep: List<Sykeforloep>
)

data class Sykeforloep(
    var oppfolgingsdato: LocalDate,
    val sykmeldinger: ArrayList<SimpleSykmelding>
)

data class SimpleSykmelding(
    val id: String,
    val fom: LocalDate,
    val tom: LocalDate
)
