package no.nav.syfo.lagrevedtak.maksdato

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import no.nav.syfo.Filter
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.SENDT_MAKSDATOMELDING
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.log
import no.nav.syfo.trefferAldersfilter

class MaksdatoService(
    private val arenaMqProducer: ArenaMqProducer,
    private val database: DatabaseInterface
) {
    private val dateFormat = "ddMMyyyy"
    private val dateTimeFormat = "ddMMyyyy,HHmmss"

    fun sendMaksdatomeldingTilArena(utbetaltEvent: UtbetaltEvent) {
        if (skalSendeMaksdatomelding(utbetaltEvent.fnr, utbetaltEvent.startdato)) {
            arenaMqProducer.sendTilArena(tilMaksdatoMelding(utbetaltEvent, OffsetDateTime.now(ZoneId.of("Europe/Oslo"))).tilMqMelding())
            log.info("Har sendt maksdatomelding for utbetaltevent {}", utbetaltEvent.utbetalteventid)
            SENDT_MAKSDATOMELDING.inc()
        }
    }

    fun skalSendeMaksdatomelding(fnr: String, startdato: LocalDate): Boolean {
        if (trefferAldersfilter(fnr, Filter.ETTER1995)) {
            return if (database.fireukersmeldingErSendt(fnr, startdato)) {
                true
            } else {
                log.info("Utbetaling gjelder sykefravær det ikke har blitt sendt 4-ukersmelding for, sender ikke maksdatomelding")
                false
            }
        }
        return false
    }

    fun tilMaksdatoMelding(utbetaltEvent: UtbetaltEvent, now: OffsetDateTime): MaksdatoMelding {
        val nowFormatted = formatDateTime(now)
        return MaksdatoMelding(
            k278M810 = K278M810(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = utbetaltEvent.fnr
            ),
            k278M815 = K278M815(),
            k278M830 = K278M830(
                startdato = formatDate(utbetaltEvent.startdato),
                maksdato = formatDate(finnMaksdato(utbetaltEvent.tom, utbetaltEvent.gjenstaendeSykedager)),
                orgnummer = utbetaltEvent.organisasjonsnummer.padEnd(9, ' ')
            ),
            k278M840 = K278M840()
        )
    }

    fun finnMaksdato(tom: LocalDate, gjenstaendeSykedager: Int): LocalDate {
        var maksdato = tom
        var counter = 0
        while (counter < gjenstaendeSykedager) {
            maksdato = maksdato.plusDays(1)
            while (maksdato.erHelg()) {
                maksdato = maksdato.plusDays(1)
            }
            counter += 1
        }
        return maksdato
    }

    private fun formatDateTime(dateTime: OffsetDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(dateTimeFormat)
        return dateTime.format(formatter)
    }

    private fun formatDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern(dateFormat)
        return formatter.format(date)
    }
}

private fun LocalDate.erHelg(): Boolean =
    dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
