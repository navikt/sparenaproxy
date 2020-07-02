package no.nav.syfo.aktivermelding

import io.ktor.util.KtorExperimentalAPI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.db.avbrytPlanlagtMelding
import no.nav.syfo.aktivermelding.db.hentPlanlagtMelding
import no.nav.syfo.aktivermelding.db.sendPlanlagtMelding
import no.nav.syfo.aktivermelding.kafka.AktiverMeldingConsumer
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log

@KtorExperimentalAPI
class AktiverMeldingService(
    private val applicationState: ApplicationState,
    private val aktiverMeldingConsumer: AktiverMeldingConsumer,
    private val database: DatabaseInterface,
    private val smregisterClient: SmregisterClient,
    private val arenaMeldingService: ArenaMeldingService
) {
    suspend fun start() {
        while (applicationState.ready) {
            val aktiverMeldinger = aktiverMeldingConsumer.poll()
            aktiverMeldinger.forEach {
                log.info("Behandler melding med id {}", it.id)
                behandleAktiverMelding(it)
            }
            delay(1)
        }
    }

    suspend fun behandleAktiverMelding(aktiverMelding: AktiverMelding) {
        val planlagtMelding = database.hentPlanlagtMelding(aktiverMelding.id)
        if (planlagtMelding != null) {
            val skalSendeMelding = smregisterClient.er100ProsentSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
            if (skalSendeMelding) {
                log.info("Sender melding med id {} til Arena", aktiverMelding.id)
                arenaMeldingService.sendPlanlagtMeldingTilArena(planlagtMelding)
                database.sendPlanlagtMelding(aktiverMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
            } else {
                log.info("Avbryter melding med id {}", aktiverMelding.id)
                database.avbrytPlanlagtMelding(aktiverMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
            }
        } else {
            log.warn("Fant ikke planlagt melding for id ${aktiverMelding.id} som ikke er sendt eller avbrutt fra før")
        }
    }
}
