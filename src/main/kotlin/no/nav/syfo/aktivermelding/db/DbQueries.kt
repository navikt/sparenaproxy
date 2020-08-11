package no.nav.syfo.aktivermelding.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.OffsetDateTime
import java.time.ZoneOffset
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

fun DatabaseInterface.avbrytPlanlagtMelding(id: UUID, avbrutt: OffsetDateTime) {
    connection.use { connection ->
        connection.avbrytPlanlagtMelding(id, avbrutt)
        connection.commit()
    }
}

fun DatabaseInterface.sendPlanlagtMelding(id: UUID, sendt: OffsetDateTime) {
    connection.use { connection ->
        connection.sendPlanlagtMelding(id, sendt)
        connection.commit()
    }
}

fun DatabaseInterface.resendAvbruttMelding(id: UUID) {
    connection.use { connection ->
        connection.resendAvbruttMelding(id)
        connection.commit()
    }
}

fun DatabaseInterface.finnPlanlagtMeldingUnderSending(fnr: String): PlanlagtMeldingDbModel? {
    connection.use { connection ->
        return connection.finnPlanlagtMeldingUnderSending(fnr)
    }
}

fun DatabaseInterface.finnAvbruttAktivitetskravmelding(fnr: String): List<PlanlagtMeldingDbModel> {
    connection.use { connection ->
        return connection.finnAvbruttAktivitetskravmelding(fnr)
    }
}

private fun Connection.hentPlanlagtMelding(id: UUID): PlanlagtMeldingDbModel? =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE id=? AND sendt is null and avbrutt is null;
            """
    ).use {
        it.setObject(1, id)
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
    }

private fun Connection.avbrytPlanlagtMelding(id: UUID, avbrutt: OffsetDateTime) =
    this.prepareStatement(
        """
            UPDATE planlagt_melding SET avbrutt=? WHERE id=?;
            """
    ).use {
        it.setTimestamp(1, Timestamp.from(avbrutt.toInstant()))
        it.setObject(2, id)
        it.execute()
    }

private fun Connection.sendPlanlagtMelding(id: UUID, sendt: OffsetDateTime) =
    this.prepareStatement(
        """
            UPDATE planlagt_melding SET sendt=? WHERE id=?;
            """
    ).use {
        it.setTimestamp(1, Timestamp.from(sendt.toInstant()))
        it.setObject(2, id)
        it.execute()
    }

private fun Connection.resendAvbruttMelding(id: UUID) =
    this.prepareStatement(
        """
            UPDATE planlagt_melding SET avbrutt=null WHERE id=?;
            """
    ).use {
        it.setObject(1, id)
        it.execute()
    }

private fun Connection.finnPlanlagtMeldingUnderSending(fnr: String): PlanlagtMeldingDbModel? =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE fnr=? AND sendt is null and avbrutt is null AND sendes<?;
            """
    ).use {
        it.setString(1, fnr)
        it.setTimestamp(2, Timestamp.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant()))
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
    }

private fun Connection.finnAvbruttAktivitetskravmelding(fnr: String): List<PlanlagtMeldingDbModel> =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE fnr=? AND type='8UKER' AND avbrutt is not null;
            """
    ).use {
        it.setString(1, fnr)
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }
    }
