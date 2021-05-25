package no.nav.syfo.lagrevedtak.client

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.http.ContentType
import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.log

@KtorExperimentalAPI
class SpokelseClient(
        private val spokelseEndpointURL: String,
        private val accessTokenClientV2: AccessTokenClientV2,
        private val resourceId: String,
        private val httpClient: HttpClient
) {

    suspend fun finnSykmeldingId(dokumenter: Set<UUID>, utbetaltEventId: UUID): UUID {
        log.info("Henter sykmeldingId for utbetaltevent {}", utbetaltEventId)
        try {
            val hendelser = hentHendelser(dokumenter)
            log.info("Fant {} dokumenter for {}", hendelser.size, utbetaltEventId)
            return hendelser.first { it.type == "Sykmelding" }.dokumentId
        } catch (e: Exception) {
            log.error("Feil ved henting av sykmeldingid for utbetaltevent $utbetaltEventId {}", e.message)
            throw e
        }
    }

    private suspend fun hentHendelser(dokumenter: Set<UUID>): List<Hendelse> =
        httpClient.get<List<Hendelse>>("$spokelseEndpointURL/dokumenter") {
            accept(ContentType.Application.Json)
            dokumenter.forEach { parameter("hendelseId", it) }
            val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
            headers {
                append("Authorization", "Bearer $accessToken")
            }
        }
}

data class Hendelse(
    val dokumentId: UUID,
    val hendelseId: UUID,
    val type: String
)
