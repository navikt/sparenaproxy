package no.nav.syfo.dodshendelser.db

import java.sql.Connection
import java.sql.Timestamp
import java.time.OffsetDateTime
import no.nav.syfo.application.db.DatabaseInterface

fun DatabaseInterface.avbrytPlanlagteMeldingerVedDodsfall(personidenter: List<String>, avbrutt: OffsetDateTime) {
    connection.use { connection ->
        personidenter.forEach {
            connection.avbrytPlanlagtMeldingVedDodsfall(it, avbrutt)
        }
        connection.commit()
    }
}

private fun Connection.avbrytPlanlagtMeldingVedDodsfall(personident: String, avbrutt: OffsetDateTime) =
    this.prepareStatement(
        """
            UPDATE planlagt_melding SET avbrutt=? WHERE fnr=? and avbrutt is null and sendt is null;
            """
    ).use {
        it.setTimestamp(1, Timestamp.from(avbrutt.toInstant()))
        it.setString(2, personident)
        it.execute()
    }
