package no.nav.syfo.lagrevedtak

import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import no.nav.syfo.Filter
import no.nav.syfo.application.metrics.MOTTATT_VEDTAK
import no.nav.syfo.application.metrics.SENDT_MAKSDATOMELDING
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.kafka.model.UtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.kafka.model.tilUtbetaltEventKafkaMessage
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import no.nav.syfo.trefferAldersfilter

@KtorExperimentalAPI
class UtbetaltEventService(
    private val spokelseClient: SpokelseClient,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val lagreUtbetaltEventOgPlanlagtMeldingService: LagreUtbetaltEventOgPlanlagtMeldingService,
    private val maksdatoService: MaksdatoService
) {
    suspend fun mottaUtbetaltEvent(record: String) {
        val jsonNode = toJsonNode(record)
        if (jsonNode["@event_name"].asText() == "utbetalt") {
            val callid = jsonNode["@id"].asText()
            log.info("Mottatt melding med callid {}", callid)
            handleUtbetaltEvent(tilUtbetaltEventKafkaMessage(jsonNode), callid)
        }
    }

    private fun toJsonNode(record: String) =
        objectMapper.readTree(record)

    suspend fun handleUtbetaltEvent(utbetaltEventKafkaMessage: UtbetaltEventKafkaMessage, callid: String) {
        MOTTATT_VEDTAK.inc()
        log.info("Behandler utbetaltEvent med id ${utbetaltEventKafkaMessage.utbetalteventid} for callid $callid")
        val sykmeldingId = spokelseClient.finnSykmeldingId(
            utbetaltEventKafkaMessage.hendelser,
            utbetaltEventKafkaMessage.utbetalteventid
        )
        val startdato = syfoSyketilfelleClient.finnStartdato(
            utbetaltEventKafkaMessage.aktorid,
            sykmeldingId.toString(),
            utbetaltEventKafkaMessage.utbetalteventid
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
            opprettet = utbetaltEventKafkaMessage.opprettet
        )

        lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

        if (skalSendeMaksdatomelding(utbetaltEvent.fnr, startdato)) {
            maksdatoService.sendMaksdatomeldingTilArena(utbetaltEvent)
            log.info("Sendt maksdatomelding for utbetalteventid ${utbetaltEventKafkaMessage.utbetalteventid}")
            SENDT_MAKSDATOMELDING.inc()
        }
    }

    fun skalSendeMaksdatomelding(fnr: String, startdato: LocalDate): Boolean {
        if (trefferAldersfilter(fnr, Filter.ETTER1995)) {
            return if (startdato.isBefore(LocalDate.now().minusWeeks(4))) {
                true
            } else {
                log.info("Utbetaling gjelder sykefravær på mindre enn 4 uker, sender ikke maksdatomelding")
                false
            }
        }
        return false
    }
}
