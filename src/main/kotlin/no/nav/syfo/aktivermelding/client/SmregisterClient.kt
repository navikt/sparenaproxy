package no.nav.syfo.aktivermelding.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.accept
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.log

class SmregisterClient(
    private val smregisterEndpointURL: String,
    private val accessTokenClientV2: AccessTokenClientV2,
    private val resourceId: String,
    private val httpClient: HttpClient
) {

    suspend fun er100ProsentSykmeldt(fnr: String, planlagtMeldingId: UUID): Boolean {
        log.info("Henter sykmeldingstatus for planlagtMelding {}", planlagtMeldingId)
        try {
            val sykmeldingstatus = hentSykmeldingstatus(fnr)
            return sykmeldingstatus.erSykmeldt && sykmeldingstatus.gradert == false
        } catch (e: Exception) {
            log.error(
                "Feil ved henting av sykmeldingstatus for planlagtMelding $planlagtMeldingId {}",
                e.message
            )
            throw e
        }
    }

    suspend fun erSykmeldt(fnr: String, planlagtMeldingId: UUID): Boolean {
        log.info(
            "Henter sykmeldingstatus uavhengig av grad for planlagtMelding {}",
            planlagtMeldingId
        )
        try {
            val sykmeldingstatus = hentSykmeldingstatus(fnr)
            return sykmeldingstatus.erSykmeldt
        } catch (e: Exception) {
            log.error(
                "Feil ved henting av sykmeldingstatus uavhengig av grad for planlagtMelding $planlagtMeldingId {}",
                e.message
            )
            throw e
        }
    }

    suspend fun erSykmeldtTilOgMed(fnr: String, planlagtMeldingId: UUID): LocalDate? {
        log.info("Henter sykmeldingstatus uavhengig av grad for stansmelding {}", planlagtMeldingId)
        try {
            val sykmeldingstatus = hentSykmeldingstatus(fnr)
            if (sykmeldingstatus.erSykmeldt && sykmeldingstatus.tom == null) {
                log.error("Bruker er sykmeldt, men sykmelding mangler tom: $planlagtMeldingId")
                throw IllegalStateException("Bruker er sykmeldt, men sykmelding mangler tom")
            }
            return sykmeldingstatus.tom
        } catch (e: Exception) {
            log.error(
                "Feil ved henting av sykmeldingstatus uavhengig av grad for stansmelding $planlagtMeldingId {}",
                e.message
            )
            throw e
        }
    }

    private suspend fun hentSykmeldingstatus(fnr: String): SykmeldtStatus =
        httpClient
            .post("$smregisterEndpointURL/api/v2/sykmelding/sykmeldtStatus") {
                accept(ContentType.Application.Json)
                contentType(ContentType.Application.Json)
                val accessToken = accessTokenClientV2.getAccessTokenV2(resourceId)
                headers { append("Authorization", "Bearer $accessToken") }
                setBody(StatusRequest(fnr = fnr))
            }
            .body<SykmeldtStatus>()
}

data class StatusRequest(val fnr: String, val dato: LocalDate = LocalDate.now())

data class SykmeldtStatus(
    val erSykmeldt: Boolean,
    val gradert: Boolean? = null,
    val fom: LocalDate? = null,
    val tom: LocalDate? = null
)
