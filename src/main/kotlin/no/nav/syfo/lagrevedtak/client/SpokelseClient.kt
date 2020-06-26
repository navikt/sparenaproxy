package no.nav.syfo.lagrevedtak.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.log

@KtorExperimentalAPI
class SpokelseClient(
    private val spokelseEndpointURL: String,
    private val accessTokenClient: AccessTokenClient,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun finnSykmeldingId(dokumenter: Set<UUID>, utbetaltEventId: UUID): UUID {
        log.info("Henter sykmeldingId for utbetaltevent {}", utbetaltEventId)
        return hentDokumenter(dokumenter).first { it.type == "Sykmelding" }.dokumentId
    }

    suspend fun hentDokumenter(dokumenter: Set<UUID>): List<Hendelse> =
        httpClient.get<DokumenterRespons>("$spokelseEndpointURL/spokelse/dokumenter") {
            accept(ContentType.Application.Json)
            dokumenter.forEach { parameter("hendelseId", it) }
            val accessToken = accessTokenClient.hentAccessToken(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
        }.hendelser
}

data class DokumenterRespons(
    val hendelser: List<Hendelse>
)

data class Hendelse(
    val dokumentId: UUID,
    val hendelseId: UUID,
    val type: String
)
