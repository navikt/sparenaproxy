package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import no.nav.syfo.client.sts.StsOidcClient
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient

@KtorExperimentalAPI
class PdlPersonService(private val pdlClient: PdlClient, private val stsOidcClient: StsOidcClient) {

    suspend fun isAlive(ident: String, meldingId: UUID): Boolean {
        val stsToken = stsOidcClient.oidcToken().access_token
        val pdlResponse = pdlClient.getPerson(ident, stsToken)

        if (pdlResponse.errors != null) {
            pdlResponse.errors.forEach {
                log.error("PDL returnerte error {}, {}", it, meldingId)
            }
        }

        if (pdlResponse.data.hentPerson == null) {
            log.error("Fant ikke person i PDL {}", meldingId)
            throw IllegalStateException("Fant ikke person i PDL")
        }

        if (pdlResponse.data.hentPerson.doedsfall.isNotEmpty()) {
            return false
        }
        return true
    }
}
