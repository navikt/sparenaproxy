package no.nav.syfo.lagrevedtak.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.log
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.toPlanlagtMeldingDbModel

fun DatabaseInterface.erBehandletTidligere(utbetalingId: UUID): Boolean {
    connection.use { connection ->
        return connection.utbetalingErBehandletTidligere(utbetalingId)
    }
}

fun DatabaseInterface.lagreUtbetaltEventOgOppdaterStansmelding(
    utbetaltEvent: UtbetaltEvent,
    planlagtStansmelding: PlanlagtMeldingDbModel
) {
    connection.use { connection ->
        val melding =
            connection.hentPlanlagtStansmelding(
                planlagtStansmelding.fnr,
                planlagtStansmelding.startdato
            )
        if (melding == null) {
            connection.lagrePlanlagtMelding(planlagtStansmelding)
        } else {
            val oppdatertSendes =
                utbetaltEvent.tom
                    .plusDays(17)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toOffsetDateTime()
            if (oppdatertSendes.isAfter(melding.sendes)) {
                connection.oppdaterStansmelding(
                    melding.id,
                    utbetaltEvent.tom
                        .plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                )
            }
        }
        if (utbetaltEvent.gjenstaendeSykedager < 66) {
            val planlagt39ukersmelding =
                connection.hentPlanlagt39ukersmelding(utbetaltEvent.fnr, utbetaltEvent.startdato)
            if (planlagt39ukersmelding != null) {
                log.info(
                    "Fremskynder utsendingstidspunkt for 39-ukersmelding med id ${planlagt39ukersmelding.id}"
                )
                connection.oppdater39ukersmelding(
                    planlagt39ukersmelding.id,
                    OffsetDateTime.now(ZoneOffset.UTC)
                )
            }
        }
        connection.lagreUtbetaltEvent(utbetaltEvent)
        connection.commit()
    }
}

fun DatabaseInterface.lagreUtbetaltEventOgPlanlagtMelding(
    utbetaltEvent: UtbetaltEvent,
    planlagteMeldinger: List<PlanlagtMeldingDbModel>
) {
    connection.use { connection ->
        connection.lagreUtbetaltEvent(utbetaltEvent)
        planlagteMeldinger.forEach { connection.lagrePlanlagtMelding(it) }
        connection.commit()
    }
}

fun DatabaseInterface.planlagtMeldingFinnes(fnr: String, startdato: LocalDate): Boolean {
    connection.use { connection ->
        return connection.planlagtMeldingFinnes(fnr, startdato)
    }
}

private fun Connection.lagreUtbetaltEvent(utbetaltEvent: UtbetaltEvent) {
    this.prepareStatement(
            """
            INSERT INTO utbetaltevent(
                utbetalteventid,
                startdato,
                aktorid,
                fnr,
                organisasjonsnummer,
                fom,
                tom,
                forbrukte_sykedager,
                gjenstaende_sykedager,
                opprettet,
                maksdato,
                utbetalingid) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             """
        )
        .use {
            it.setObject(1, utbetaltEvent.utbetalteventid)
            it.setObject(2, utbetaltEvent.startdato)
            it.setString(3, utbetaltEvent.aktorid)
            it.setString(4, utbetaltEvent.fnr)
            it.setString(5, utbetaltEvent.organisasjonsnummer)
            it.setObject(6, utbetaltEvent.fom)
            it.setObject(7, utbetaltEvent.tom)
            it.setInt(8, utbetaltEvent.forbrukteSykedager)
            it.setInt(9, utbetaltEvent.gjenstaendeSykedager)
            it.setTimestamp(10, Timestamp.valueOf(utbetaltEvent.opprettet))
            it.setObject(11, utbetaltEvent.maksdato)
            it.setObject(12, utbetaltEvent.utbetalingId)
            it.execute()
        }
}

private fun Connection.hentPlanlagtStansmelding(
    fnr: String,
    startdato: LocalDate
): PlanlagtMeldingDbModel? =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND startdato=? AND type='STANS' AND sendt is null AND avbrutt is null;
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
        }

private fun Connection.oppdaterStansmelding(id: UUID, sendes: OffsetDateTime) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET sendes=?, avbrutt=null  WHERE id=?;
            """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(sendes.toInstant()))
            it.setObject(2, id)
            it.execute()
        }

private fun Connection.lagrePlanlagtMelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel) {
    this.prepareStatement(
            """
            INSERT INTO planlagt_melding(
                id,
                fnr,
                startdato,
                type,
                opprettet,
                sendes)
            VALUES (?, ?, ?, ?, ?, ?)
             """
        )
        .use {
            it.setObject(1, planlagtMeldingDbModel.id)
            it.setString(2, planlagtMeldingDbModel.fnr)
            it.setObject(3, planlagtMeldingDbModel.startdato)
            it.setString(4, planlagtMeldingDbModel.type)
            it.setTimestamp(5, Timestamp.from(planlagtMeldingDbModel.opprettet.toInstant()))
            it.setTimestamp(6, Timestamp.from(planlagtMeldingDbModel.sendes.toInstant()))
            it.execute()
        }
}

private fun Connection.planlagtMeldingFinnes(fnr: String, startdato: LocalDate): Boolean =
    this.prepareStatement(
            """
            SELECT 1 FROM planlagt_melding WHERE fnr=? AND startdato=?;
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().next()
        }

private fun Connection.hentPlanlagt39ukersmelding(
    fnr: String,
    startdato: LocalDate
): PlanlagtMeldingDbModel? =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND startdato=? AND type='39UKER' AND sendt is null AND avbrutt is null;
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
        }

private fun Connection.oppdater39ukersmelding(id: UUID, sendes: OffsetDateTime) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET sendes=?, avbrutt=null  WHERE id=?;
            """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(sendes.toInstant()))
            it.setObject(2, id)
            it.execute()
        }

private fun Connection.utbetalingErBehandletTidligere(utbetalingId: UUID): Boolean =
    this.prepareStatement(
            """
            SELECT 1 FROM utbetaltevent WHERE utbetalingid=?;
            """
        )
        .use {
            it.setObject(1, utbetalingId)
            it.executeQuery().next()
        }
