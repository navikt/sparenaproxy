package no.nav.syfo.aktivermelding.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.log

@KtorExperimentalAPI
class SmregisterClient(
    private val spokelseEndpointURL: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun er100ProsentSykmeldt(fnr: String, planlagtMeldingId: UUID): Boolean {
        log.info("Henter sykmeldingstatus for planlagtMelding {}", planlagtMeldingId)
        try {
            val sykmeldingstatus = hentSykmeldingstatus(fnr)
            return !(!sykmeldingstatus.erSykmeldt || (sykmeldingstatus.grad != null && sykmeldingstatus.grad < 100))
        } catch (e: Exception) {
            log.error("Feil ved henting av sykmeldingstatus for planlagtMelding $planlagtMeldingId {}", e.message)
            throw e
        }
    }

    private suspend fun hentSykmeldingstatus(fnr: String): Sykmeldingstatus =
        httpClient.post<Sykmeldingstatus>("$spokelseEndpointURL/dokumenter") {
            accept(ContentType.Application.Json)
            val accessToken = accessTokenClient.hentAccessToken(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
            body = StatusRequest(fnr = fnr)
        }
}

data class StatusRequest(
    val fnr: String,
    val dato: LocalDate = LocalDate.now()
)

data class Sykmeldingstatus(
    val erSykmeldt: Boolean,
    val grad: Int?
)
