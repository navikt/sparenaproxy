package no.nav.syfo.lagrevedtak.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.objectMapper
import org.postgresql.util.PGobject

fun DatabaseInterface.lagreUtbetaltEvent(
    utbetaltEvent: UtbetaltEvent
) {
    connection.use { connection ->
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
        planlagteMeldinger.forEach {
            connection.lagrePlanlagtMelding(it)
        }
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
                sykmeldingid,
                aktorid,
                fnr,
                organisasjonsnummer,
                hendelser,
                oppdrag,
                fom,
                tom,
                forbrukte_sykedager,
                gjenstaende_sykedager,
                opprettet) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
             """
    ).use {
        it.setObject(1, utbetaltEvent.utbetalteventid)
        it.setObject(2, utbetaltEvent.startdato)
        it.setObject(3, utbetaltEvent.sykmeldingid)
        it.setString(4, utbetaltEvent.aktorid)
        it.setString(5, utbetaltEvent.fnr)
        it.setString(6, utbetaltEvent.organisasjonsnummer)
        it.setObject(7, PGobject().apply {
            type = "json"
            value = objectMapper.writeValueAsString(utbetaltEvent.hendelser)
        })
        it.setObject(8, PGobject().apply {
            type = "json"
            value = objectMapper.writeValueAsString(utbetaltEvent.oppdrag)
        })
        it.setObject(9, utbetaltEvent.fom)
        it.setObject(10, utbetaltEvent.tom)
        it.setInt(11, utbetaltEvent.forbrukteSykedager)
        it.setInt(12, utbetaltEvent.gjenstaendeSykedager)
        it.setTimestamp(13, Timestamp.valueOf(utbetaltEvent.opprettet))
        it.execute()
    }
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
    ).use {
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
    ).use {
        it.setString(1, fnr)
        it.setObject(2, startdato)
        it.executeQuery().next()
    }
