package no.nav.syfo.aktivermelding.db

import java.sql.Connection
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

fun Connection.hentPlanlagtMelding(id: UUID): PlanlagtMeldingDbModel? =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE id=? AND sendt is null and avbrutt is null;
            """
    ).use {
        it.setObject(1, id)
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
    }
