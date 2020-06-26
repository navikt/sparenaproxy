package no.nav.syfo.lagrevedtak

import java.time.LocalDate
import java.util.UUID
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.lagrevedtak.kafka.UtbetaltEventConsumer
import no.nav.syfo.lagrevedtak.kafka.model.UtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.kafka.model.tilUtbetaltEventKafkaMessage
import no.nav.syfo.log

class VedtakService(
    private val applicationState: ApplicationState,
    private val utbetaltEventConsumer: UtbetaltEventConsumer,
    private val lagreUtbetaltEventOgPlanlagtMeldingService: LagreUtbetaltEventOgPlanlagtMeldingService
) {
    fun start() {
        while (applicationState.ready) {
            val jsonNodes = utbetaltEventConsumer.poll()
            jsonNodes.forEach {
                log.info("Lest melding fra topic")
                if (it["@event_name"].asText() == "utbetalt") {
                    handleUtbetaltEvent(tilUtbetaltEventKafkaMessage(it))
                } else {
                    log.info("Mottatt melding med annen type, ignorerer...")
                }
            }
        }
    }

    fun handleUtbetaltEvent(utbetaltEventKafkaMessage: UtbetaltEventKafkaMessage) {
        // finn sykmeldingsid fra nytt api (kommer)
        // slå opp i syfosyketilfelle for å finne startdato for riktig sykeforløp (kommer)
        // lagre utbetaltevent med startdato, samt planlagt varsel, evt oppdatere planlagt varsel
        log.info("Behandler utbetaltEvent med id ${utbetaltEventKafkaMessage.utbetalteventid}")
        val utbetaltEvent = UtbetaltEvent(
            utbetalteventid = utbetaltEventKafkaMessage.utbetalteventid,
            startdato = LocalDate.now(), // hentes fra syfosyketilfelle
            sykmeldingid = UUID.randomUUID(), // hentes fra nytt api
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
