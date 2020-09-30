package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.db.avbrytPlanlagtMelding
import no.nav.syfo.aktivermelding.db.hentPlanlagtMelding
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.AVBRUTT_MELDING
import no.nav.syfo.application.metrics.IKKE_FUNNET_MELDING
import no.nav.syfo.application.metrics.MOTTATT_AKTIVERMELDING
import no.nav.syfo.application.metrics.SENDT_MELDING
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.objectMapper

@KtorExperimentalAPI
class AktiverMeldingService(
    private val database: DatabaseInterface,
    private val smregisterClient: SmregisterClient,
    private val arenaMeldingService: ArenaMeldingService
) {
    suspend fun handleAktiverMelding(record: String) {
        val aktiverMelding: AktiverMelding = objectMapper.readValue(record)
        log.info("Behandler melding med id {}", aktiverMelding.id)
        MOTTATT_AKTIVERMELDING.inc()
        behandleAktiverMelding(aktiverMelding)
    }

    suspend fun behandleAktiverMelding(aktiverMelding: AktiverMelding) {
        val planlagtMelding = database.hentPlanlagtMelding(aktiverMelding.id)
        if (planlagtMelding != null) {
            val skalSendeMelding = when (planlagtMelding.type) {
                BREV_4_UKER_TYPE -> {
                    smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                }
                AKTIVITETSKRAV_8_UKER_TYPE -> {
                    smregisterClient.er100ProsentSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                }
                BREV_39_UKER_TYPE -> {
                    smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                }
                else -> {
                    log.error("Planlagt melding med id ${planlagtMelding.id} har ukjent type: ${planlagtMelding.type}")
                    throw IllegalStateException("Planlagt melding har ukjent type")
                }
            }
            if (skalSendeMelding) {
                log.info("Sender melding med id {} til Arena", aktiverMelding.id)
                arenaMeldingService.sendPlanlagtMeldingTilArena(planlagtMelding)
                SENDT_MELDING.inc()
            } else {
                log.info("Avbryter melding med id {}", aktiverMelding.id)
                database.avbrytPlanlagtMelding(aktiverMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
                AVBRUTT_MELDING.inc()
            }
        } else {
            log.warn("Fant ikke planlagt melding for id ${aktiverMelding.id} som ikke er sendt eller avbrutt fra før")
            IKKE_FUNNET_MELDING.inc()
        }
    }
}
