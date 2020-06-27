package no.nav.syfo.lagrevedtak

import io.ktor.util.KtorExperimentalAPI
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.kafka.UtbetaltEventConsumer
import no.nav.syfo.lagrevedtak.kafka.model.UtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.kafka.model.tilUtbetaltEventKafkaMessage
import no.nav.syfo.log
import no.nav.syfo.objectMapper

@KtorExperimentalAPI
class VedtakService(
    private val applicationState: ApplicationState,
    private val utbetaltEventConsumer: UtbetaltEventConsumer,
    private val spokelseClient: SpokelseClient,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val lagreUtbetaltEventOgPlanlagtMeldingService: LagreUtbetaltEventOgPlanlagtMeldingService
) {
    suspend fun start() {
        while (applicationState.ready) {
            val jsonNodesAsString = utbetaltEventConsumer.poll()
            jsonNodesAsString.forEach {
                val jsonNode = objectMapper.readTree(it)
                if (jsonNode["@event_name"].asText() == "utbetalt") {
                    handleUtbetaltEvent(tilUtbetaltEventKafkaMessage(jsonNode))
                }
            }
            delay(1)
        }
    }

    suspend fun handleUtbetaltEvent(utbetaltEventKafkaMessage: UtbetaltEventKafkaMessage) {
        log.info("Behandler utbetaltEvent med id ${utbetaltEventKafkaMessage.utbetalteventid}")
        // val sykmeldingId = spokelseClient.finnSykmeldingId(utbetaltEventKafkaMessage.hendelser, utbetaltEventKafkaMessage.utbetalteventid)
        val sykmeldingId = UUID.fromString("ef9b076a-acd4-4219-a6d4-e4107d9023cb")
        val startdato = syfoSyketilfelleClient.finnStartdato(utbetaltEventKafkaMessage.aktorid, sykmeldingId.toString(), utbetaltEventKafkaMessage.utbetalteventid)
        val utbetaltEvent = UtbetaltEvent(
            utbetalteventid = utbetaltEventKafkaMessage.utbetalteventid,
            startdato = startdato,
            sykmeldingid = sykmeldingId,
            aktorid = utbetaltEventKafkaMessage.aktorid,
            fnr = utbetaltEventKafkaMessage.fnr,
            organisasjonsnummer = utbetaltEventKafkaMessage.organisasjonsnummer,
            hendelser = utbetaltEventKafkaMessage.hendelser,
            oppdrag = utbetaltEventKafkaMessage.oppdrag,
            fom = utbetaltEventKafkaMessage.fom,
            tom = utbetaltEventKafkaMessage.tom,
            forbrukteSykedager = utbetaltEventKafkaMessage.forbrukteSykedager,
            gjenstaendeSykedager = utbetaltEventKafkaMessage.gjenstaendeSykedager,
            opprettet = utbetaltEventKafkaMessage.opprettet
        )

        lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
    }
}
