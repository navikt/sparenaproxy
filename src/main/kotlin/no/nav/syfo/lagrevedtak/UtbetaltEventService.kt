package no.nav.syfo.lagrevedtak

import java.time.LocalDateTime
import no.nav.syfo.application.metrics.MOTTATT_VEDTAK
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.kafka.model.UtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.kafka.model.tilUtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import no.nav.syfo.log
import no.nav.syfo.objectMapper

class UtbetaltEventService(
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val lagreUtbetaltEventOgPlanlagtMeldingService:
        LagreUtbetaltEventOgPlanlagtMeldingService,
    private val maksdatoService: MaksdatoService
) {
    suspend fun mottaUtbetaltEvent(record: String) {
        val jsonNode = toJsonNode(record)
        if (jsonNode["event"]?.asText() == "utbetaling_utbetalt") {
            val utbetalingId = jsonNode["utbetalingId"].asText()
            log.info("Mottatt melding med utbetalingId {}", utbetalingId)
            handleUtbetaltEvent(tilUtbetaltEventKafkaMessage(jsonNode))
        }
    }

    private fun toJsonNode(record: String) = objectMapper.readTree(record)

    suspend fun handleUtbetaltEvent(utbetaltEventKafkaMessage: UtbetaltEventKafkaMessage) {
        if (
            lagreUtbetaltEventOgPlanlagtMeldingService.erBehandletTidligere(
                utbetaltEventKafkaMessage.utbetalingId
            )
        ) {
            log.info(
                "Utbetaling med id ${utbetaltEventKafkaMessage.utbetalingId} er behandlet tidligere, ignorerer meldingen"
            )
            return
        }
        MOTTATT_VEDTAK.inc()
        log.info("Behandler utbetaling med id ${utbetaltEventKafkaMessage.utbetalingId}")

        val startdato =
            syfoSyketilfelleClient.finnStartdato(
                fnr = utbetaltEventKafkaMessage.fnr,
                fom = utbetaltEventKafkaMessage.fom,
                tom = utbetaltEventKafkaMessage.tom,
                sporingsId = utbetaltEventKafkaMessage.utbetalteventid
            )
        val utbetaltEvent =
            UtbetaltEvent(
                utbetalteventid = utbetaltEventKafkaMessage.utbetalteventid,
                startdato = startdato,
                aktorid = utbetaltEventKafkaMessage.aktorid,
                fnr = utbetaltEventKafkaMessage.fnr,
                organisasjonsnummer = utbetaltEventKafkaMessage.organisasjonsnummer,
                fom = utbetaltEventKafkaMessage.fom,
                tom = utbetaltEventKafkaMessage.tom,
                forbrukteSykedager = utbetaltEventKafkaMessage.forbrukteSykedager,
                gjenstaendeSykedager = utbetaltEventKafkaMessage.gjenstaendeSykedager,
                opprettet = LocalDateTime.now(),
                maksdato = utbetaltEventKafkaMessage.maksdato,
                utbetalingId = utbetaltEventKafkaMessage.utbetalingId
            )

        lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
            utbetaltEvent
        )

        maksdatoService.sendMaksdatomeldingTilArena(utbetaltEvent)
    }
}
