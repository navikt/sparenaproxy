package no.nav.syfo.lagrevedtak

import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.KUN_LAGRET_VEDTAK
import no.nav.syfo.application.metrics.OPPRETTET_PLANLAGT_MELDING
import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEventOgOppdaterStansmelding
import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEventOgPlanlagtMelding
import no.nav.syfo.lagrevedtak.db.planlagtMeldingFinnes
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE

class LagreUtbetaltEventOgPlanlagtMeldingService(private val database: DatabaseInterface) {

    fun lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent: UtbetaltEvent) {
        val planlagtStansmelding = lagPlanlagtMeldingDbModelForUtbetaling(
            utbetaltEvent,
            STANS_TYPE,
            utbetaltEvent.tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        )
        if (database.planlagtMeldingFinnes(utbetaltEvent.fnr, utbetaltEvent.startdato)) {
            log.info("Meldinger er allerede opprettet, lagrer nytt utbetalingsevent {}", utbetaltEvent.utbetalteventid)
            KUN_LAGRET_VEDTAK.inc()
            database.lagreUtbetaltEventOgOppdaterStansmelding(utbetaltEvent, planlagtStansmelding)
        } else {
            log.info("Lagrer utbetalingsevent {} og planlagte meldinger", utbetaltEvent.utbetalteventid)
            OPPRETTET_PLANLAGT_MELDING.labels(BREV_4_UKER_TYPE).inc()
            OPPRETTET_PLANLAGT_MELDING.labels(AKTIVITETSKRAV_8_UKER_TYPE).inc()
            OPPRETTET_PLANLAGT_MELDING.labels(BREV_39_UKER_TYPE).inc()
            OPPRETTET_PLANLAGT_MELDING.labels(STANS_TYPE).inc()
            database.lagreUtbetaltEventOgPlanlagtMelding(
                utbetaltEvent,
                listOf(
                    lagPlanlagtMeldingDbModelForUtbetaling(
                        utbetaltEvent,
                        BREV_4_UKER_TYPE,
                        utbetaltEvent.startdato.plusWeeks(4).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                    ),
                    lagPlanlagtMeldingDbModelForUtbetaling(
                        utbetaltEvent,
                        AKTIVITETSKRAV_8_UKER_TYPE,
                        utbetaltEvent.startdato.plusWeeks(8).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
                    ),
                    lagPlanlagtMeldingDbModelForUtbetaling(
                        utbetaltEvent,
                        BREV_39_UKER_TYPE,
                        finnUtsendingstidspunkt39Ukersmelding(utbetaltEvent)
                    ),
                    planlagtStansmelding
                )
            )
        }
    }

    private fun finnUtsendingstidspunkt39Ukersmelding(utbetaltEvent: UtbetaltEvent): OffsetDateTime =
        if (utbetaltEvent.gjenstaendeSykedager < 66) {
            OffsetDateTime.now(ZoneOffset.UTC)
        } else {
            utbetaltEvent.startdato.plusWeeks(39).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
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
