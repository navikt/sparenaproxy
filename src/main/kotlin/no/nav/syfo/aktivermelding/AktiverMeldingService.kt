package no.nav.syfo.aktivermelding

import io.ktor.util.KtorExperimentalAPI
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.db.hentPlanlagtMelding
import no.nav.syfo.aktivermelding.kafka.AktiverMeldingConsumer
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.log

@KtorExperimentalAPI
class AktiverMeldingService(
    private val applicationState: ApplicationState,
    private val aktiverMeldingConsumer: AktiverMeldingConsumer,
    private val database: DatabaseInterface,
    private val smregisterClient: SmregisterClient,
    private val arenaMqProducer: ArenaMqProducer
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
                log.info("Oppretter melding til Arena... {}", aktiverMelding.id)
                // opprett riktig melding
                // send til arena
            } else {
                log.info("Avbryter melding.. {}", aktiverMelding.id)
                // avbryt
            }
        } else {
            log.warn("Fant ikke planlagt melding for id ${aktiverMelding.id} som ikke er sendt eller avbrutt fra før")
        }
    }
}
