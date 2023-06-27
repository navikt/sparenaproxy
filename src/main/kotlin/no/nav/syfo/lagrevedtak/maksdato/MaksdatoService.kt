package no.nav.syfo.lagrevedtak.maksdato

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.application.metrics.SENDT_MAKSDATOMELDING
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.log
import no.nav.syfo.pdl.service.PdlPersonService

class MaksdatoService(
    private val arenaMqProducer: ArenaMqProducer,
    private val pdlPersonService: PdlPersonService
) {
    private val dateFormat = "ddMMyyyy"
    private val dateTimeFormat = "ddMMyyyy,HHmmss"

    suspend fun sendMaksdatomeldingTilArena(utbetaltEvent: UtbetaltEvent) {
        if (
            skalSendeMaksdatomelding(
                utbetaltEvent.fnr,
                utbetaltEvent.forbrukteSykedager,
                utbetaltEvent.utbetalteventid
            )
        ) {
            val correlationId =
                arenaMqProducer.sendTilArena(
                    tilMaksdatoMelding(utbetaltEvent, OffsetDateTime.now(ZoneId.of("Europe/Oslo")))
                        .tilMqMelding()
                )
            log.info(
                "Har sendt maksdatomelding for utbetaltevent {}, correlationId: {}",
                utbetaltEvent.utbetalteventid,
                correlationId
            )
            SENDT_MAKSDATOMELDING.inc()
        }
    }

    suspend fun skalSendeMaksdatomelding(
        fnr: String,
        forbrukteSykedager: Int,
        utbetalteventid: UUID
    ): Boolean {
        return if (forbrukteSykedager >= 20) {
            if (pdlPersonService.isAlive(fnr, utbetalteventid)) {
                true
            } else {
                log.info("Person er død, sender ikke maksdatomelding for $utbetalteventid")
                return false
            }
        } else {
            log.info(
                "Utbetaling gjelder sykefravær som har vart kortere enn 4 uker, sender ikke maksdatomelding"
            )
            false
        }
    }

    fun tilMaksdatoMelding(utbetaltEvent: UtbetaltEvent, now: OffsetDateTime): MaksdatoMelding {
        val nowFormatted = formatDateTime(now)
        return MaksdatoMelding(
            k278M810 =
                K278M810(
                    dato = nowFormatted.split(',')[0],
                    klokke = nowFormatted.split(',')[1],
                    fnr = utbetaltEvent.fnr
                ),
            k278M815 = K278M815(),
            k278M830 =
                K278M830(
                    startdato = formatDate(utbetaltEvent.startdato),
                    maksdato = formatDate(utbetaltEvent.maksdato),
                    orgnummer = utbetaltEvent.organisasjonsnummer.padEnd(9, ' ')
                ),
            k278M840 = K278M840()
        )
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
