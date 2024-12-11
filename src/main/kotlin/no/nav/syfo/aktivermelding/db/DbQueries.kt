package no.nav.syfo.aktivermelding.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.toPlanlagtMeldingDbModel

fun DatabaseInterface.hentPlanlagtMelding(id: UUID): PlanlagtMeldingDbModel? {
    connection.use { connection ->
        return connection.hentPlanlagtMelding(id)
    }
}

fun DatabaseInterface.finnesNyerePlanlagtMeldingMedAnnenStartdato(
    fnr: String,
    startdato: LocalDate,
    opprettet: OffsetDateTime
): Boolean {
    connection.use { connection ->
        return connection.finnesNyerePlanlagtMeldingMedAnnenStartdato(fnr, startdato, opprettet)
    }
}

fun DatabaseInterface.avbrytPlanlagtMelding(id: UUID, avbrutt: OffsetDateTime) {
    connection.use { connection ->
        connection.avbrytPlanlagtMelding(id, avbrutt)
        connection.commit()
    }
}

fun DatabaseInterface.utsettPlanlagtMelding(id: UUID, sendes: OffsetDateTime) {
    connection.use { connection ->
        connection.utsettPlanlagtMelding(id, sendes)
        connection.commit()
    }
}

fun DatabaseInterface.sendPlanlagtMelding(id: UUID, sendt: OffsetDateTime, correlationId: String) {
    connection.use { connection ->
        connection.sendPlanlagtMelding(id, sendt, correlationId)
        connection.commit()
    }
}

fun DatabaseInterface.resendPlanlagtMelding(correlationId: String): Int {
    connection.use { connection ->
        val antallResendteMeldinger = connection.resendMelding(correlationId)
        connection.commit()
        return antallResendteMeldinger
    }
}

fun DatabaseInterface.resendAvbruttMelding(id: UUID) {
    connection.use { connection ->
        connection.resendAvbruttMelding(id)
        connection.commit()
    }
}

fun DatabaseInterface.finnAvbruttAktivitetskravmelding(fnr: String): List<PlanlagtMeldingDbModel> {
    connection.use { connection ->
        return connection.finnAvbruttAktivitetskravmelding(fnr)
    }
}

fun DatabaseInterface.finnAktivStansmelding(fnr: String): List<PlanlagtMeldingDbModel> {
    connection.use { connection ->
        return connection.finnAktiveStansmeldinger(fnr)
    }
}

fun DatabaseInterface.erStansmeldingSendt(fnr: String, startdato: LocalDate): Boolean {
    connection.use { connection ->
        return connection.erStansmeldingSendt(fnr, startdato)
    }
}

private fun Connection.hentPlanlagtMelding(id: UUID): PlanlagtMeldingDbModel? =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE id=? AND sendt is null and avbrutt is null;
            """
        )
        .use {
            it.setObject(1, id)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
        }

private fun Connection.finnesNyerePlanlagtMeldingMedAnnenStartdato(
    fnr: String,
    startdato: LocalDate,
    opprettet: OffsetDateTime
): Boolean =
    this.prepareStatement(
            """
            SELECT 1 FROM planlagt_melding WHERE fnr=? AND startdato!=? AND opprettet>?;
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.setTimestamp(3, Timestamp.from(opprettet.toInstant()))
            it.executeQuery().next()
        }

private fun Connection.avbrytPlanlagtMelding(id: UUID, avbrutt: OffsetDateTime) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET avbrutt=? WHERE id=?;
            """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(avbrutt.toInstant()))
            it.setObject(2, id)
            it.execute()
        }

private fun Connection.utsettPlanlagtMelding(id: UUID, sendes: OffsetDateTime) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET sendes=? WHERE id=?;
            """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(sendes.toInstant()))
            it.setObject(2, id)
            it.execute()
        }

private fun Connection.sendPlanlagtMelding(id: UUID, sendt: OffsetDateTime, correlationId: String) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET sendt=?, jmscorrelationid=? WHERE id=?;
            """
        )
        .use {
            it.setTimestamp(1, Timestamp.from(sendt.toInstant()))
            it.setString(2, correlationId)
            it.setObject(3, id)
            it.execute()
        }

private fun Connection.resendMelding(correlationId: String): Int =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET sendt=null, jmscorrelationid=null WHERE jmscorrelationid=?;
            """
        )
        .use {
            it.setObject(1, correlationId)
            it.executeUpdate()
        }

private fun Connection.resendAvbruttMelding(id: UUID) =
    this.prepareStatement(
            """
            UPDATE planlagt_melding SET avbrutt=null WHERE id=?;
            """
        )
        .use {
            it.setObject(1, id)
            it.execute()
        }

private fun Connection.finnAvbruttAktivitetskravmelding(fnr: String): List<PlanlagtMeldingDbModel> =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND type='8UKER' AND avbrutt is not null;
            """
        )
        .use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }
        }

private fun Connection.finnAktiveStansmeldinger(fnr: String): List<PlanlagtMeldingDbModel> =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND type='STANS' AND sendt is null AND avbrutt is null;
            """
        )
        .use {
            it.setString(1, fnr)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }
        }

private fun Connection.erStansmeldingSendt(fnr: String, startdato: LocalDate): Boolean =
    this.prepareStatement(
            """
            SELECT 1 FROM planlagt_melding WHERE fnr=? AND startdato=? AND type='STANS' AND (sendt is not null OR avbrutt is not null);
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().next()
        }
