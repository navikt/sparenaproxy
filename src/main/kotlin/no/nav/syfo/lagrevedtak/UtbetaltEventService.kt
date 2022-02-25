package no.nav.syfo.lagrevedtak

import no.nav.syfo.application.metrics.MOTTATT_VEDTAK
import no.nav.syfo.application.metrics.ULIK_TOM
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.kafka.model.UtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.kafka.model.tilUtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class UtbetaltEventService(
    private val spokelseClient: SpokelseClient,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val lagreUtbetaltEventOgPlanlagtMeldingService: LagreUtbetaltEventOgPlanlagtMeldingService,
    private val maksdatoService: MaksdatoService
) {
    suspend fun mottaUtbetaltEvent(record: String) {
        val jsonNode = toJsonNode(record)
        if (jsonNode["@event_name"]?.asText() == "utbetalt") {
            val callid = jsonNode["@id"].asText()
            log.info("Mottatt melding med callid {}", callid)
            handleUtbetaltEvent(tilUtbetaltEventKafkaMessage(jsonNode), callid)
        }
    }

    private fun toJsonNode(record: String) =
        objectMapper.readTree(record)

    suspend fun handleUtbetaltEvent(utbetaltEventKafkaMessage: UtbetaltEventKafkaMessage, callid: String) {
        MOTTATT_VEDTAK.inc()
        log.info("Behandler utbetaltEvent med id ${utbetaltEventKafkaMessage.utbetalteventid} for callid $callid og utbetalingId ${utbetaltEventKafkaMessage.utbetalingId}")
        val sykmeldingId = spokelseClient.finnSykmeldingId(
            utbetaltEventKafkaMessage.hendelser,
            utbetaltEventKafkaMessage.utbetalteventid
        )
        val startdato = syfoSyketilfelleClient.finnStartdato(
            fnr = utbetaltEventKafkaMessage.fnr,
            sykmeldingId = sykmeldingId.toString(),
            fom = utbetaltEventKafkaMessage.utbetalingFom ?: utbetaltEventKafkaMessage.fom,
            tom = utbetaltEventKafkaMessage.utbetalingTom ?: utbetaltEventKafkaMessage.tom,
            sporingsId = utbetaltEventKafkaMessage.utbetalteventid
        )
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
            opprettet = utbetaltEventKafkaMessage.opprettet,
            maksdato = utbetaltEventKafkaMessage.maksdato,
            utbetalingId = utbetaltEventKafkaMessage.utbetalingId,
            utbetalingFom = utbetaltEventKafkaMessage.utbetalingFom,
            utbetalingTom = utbetaltEventKafkaMessage.utbetalingTom
        )

        if (utbetaltEvent.tom != utbetaltEvent.utbetalingTom) {
            log.warn(
                "Tom ${formaterDato(utbetaltEvent.tom)} er ikke lik utbetalingTom ${formaterDato(utbetaltEvent.utbetalingTom)} " +
                    "for utbetaltevent ${utbetaltEvent.utbetalteventid} med utbetalingId ${utbetaltEvent.utbetalingId}"
            )
            ULIK_TOM.inc()
        }

        lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

        maksdatoService.sendMaksdatomeldingTilArena(utbetaltEvent)
    }

    private fun formaterDato(dato: LocalDate?): String {
        val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
        return dato?.format(formatter) ?: "null"
    }
}
