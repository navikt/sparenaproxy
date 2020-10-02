package no.nav.syfo.lagrevedtak.maksdato

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.log

class MaksdatoService(
    private val arenaMqProducer: ArenaMqProducer
) {
    private val dateFormat = "ddMMyyyy"
    private val dateTimeFormat = "ddMMyyyy,HHmmss"

    fun sendMaksdatomeldingTilArena(utbetaltEvent: UtbetaltEvent) {
        arenaMqProducer.sendTilArena(tilMaksdatoMelding(utbetaltEvent, OffsetDateTime.now(ZoneId.of("Europe/Oslo"))).tilMqMelding())
        log.info("Har sendt maksdatomelding for utbetaltevent {}", utbetaltEvent.utbetalteventid)
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
                maksdato = formatDate(finnMaksdato(utbetaltEvent)),
                orgnummer = utbetaltEvent.organisasjonsnummer.padEnd(9, ' ')
            ),
            k278M840 = K278M840()
        )
    }

    fun finnMaksdato(utbetaltEvent: UtbetaltEvent): LocalDate =
        utbetaltEvent.opprettet.toLocalDate().plusDays(utbetaltEvent.gjenstaendeSykedager.toLong())

    private fun formatDateTime(dateTime: OffsetDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(dateTimeFormat)
        return dateTime.format(formatter)
    }

    private fun formatDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern(dateFormat)
        return formatter.format(date)
    }
}
