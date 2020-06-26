package no.nav.syfo.lagrevedtak

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.lagrevedtak.db.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.lagrevedtak.db.PlanlagtMeldingDbModel
import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEvent
import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEventOgPlanlagtMelding
import no.nav.syfo.lagrevedtak.db.planlagtMeldingFinnes
import no.nav.syfo.log

class LagreUtbetaltEventOgPlanlagtMeldingService(private val database: DatabaseInterface) {

    fun lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent: UtbetaltEvent) {
        if (database.planlagtMeldingFinnes(utbetaltEvent.fnr, utbetaltEvent.startdato)) {
            log.info("Meldinger er allerede opprettet, lagrer nytt utbetalingsevent {}", utbetaltEvent.utbetalteventid)
            database.lagreUtbetaltEvent(utbetaltEvent)
        } else {
            log.info("Lagrer utbetalingsevent {} og planlagte meldinger", utbetaltEvent.utbetalteventid)
            database.lagreUtbetaltEventOgPlanlagtMelding(
                utbetaltEvent,
                listOf(lagPlanlagtMeldingDbModelForUtbetaling(
                    utbetaltEvent,
                    AKTIVITETSKRAV_8_UKER_TYPE,
                    utbetaltEvent.startdato.plusWeeks(8).atStartOfDay()
                        .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                ))
            )
        }
    }

    private fun lagPlanlagtMeldingDbModelForUtbetaling(utbetaltEvent: UtbetaltEvent, type: String, sendes: OffsetDateTime): PlanlagtMeldingDbModel {
        return PlanlagtMeldingDbModel(
            id = UUID.randomUUID(),
            fnr = utbetaltEvent.fnr,
            startdato = utbetaltEvent.startdato,
            type = type,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC),
            sendes = sendes
        )
    }
}
