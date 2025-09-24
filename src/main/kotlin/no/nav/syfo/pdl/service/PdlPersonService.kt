package no.nav.syfo.pdl.service

import java.util.UUID
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.log
import no.nav.syfo.pdl.client.PdlClient

class PdlPersonService(
    private val pdlClient: PdlClient,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val pdlScope: String
) {
    suspend fun isAlive(ident: String, meldingId: UUID): Boolean {
        val accessToken = accessTokenClientV2.getAccessTokenV2(pdlScope)
        val pdlResponse = pdlClient.getPerson(ident, accessToken)

        pdlResponse.errors?.forEach { log.error("PDL returnerte error {}, {}", it, meldingId) }

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
