package no.nav.syfo.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.lagrevedtak.Utbetalt
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.toPlanlagtMeldingDbModel
import no.nav.syfo.objectMapper
import org.flywaydb.core.Flyway

class TestDB : DatabaseInterface {
    private var pg: EmbeddedPostgres? = null
    override val connection: Connection
        get() = pg!!.postgresDatabase.connection.apply { autoCommit = false }

    init {
        pg = EmbeddedPostgres.start()
        Flyway.configure().run {
            dataSource(pg?.postgresDatabase).load().migrate()
        }
    }

    fun stop() {
        pg?.close()
    }
}

fun Connection.dropData() {
    use { connection ->
        connection.prepareStatement("DELETE FROM planlagt_melding").executeUpdate()
        connection.prepareStatement("DELETE FROM utbetaltevent").executeUpdate()
        connection.commit()
    }
}

fun Connection.lagrePlanlagtMelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel) {
    this.prepareStatement(
        """
            INSERT INTO planlagt_melding(
                id,
                fnr,
                startdato,
                type,
                opprettet,
                sendes,
                avbrutt,
                sendt,
                jmscorrelationid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
             """
    ).use {
        it.setObject(1, planlagtMeldingDbModel.id)
        it.setString(2, planlagtMeldingDbModel.fnr)
        it.setObject(3, planlagtMeldingDbModel.startdato)
        it.setString(4, planlagtMeldingDbModel.type)
        it.setTimestamp(5, Timestamp.from(planlagtMeldingDbModel.opprettet.toInstant()))
        it.setTimestamp(6, Timestamp.from(planlagtMeldingDbModel.sendes.toInstant()))
        it.setTimestamp(7, if (planlagtMeldingDbModel.avbrutt != null) { Timestamp.from(planlagtMeldingDbModel.avbrutt?.toInstant()) } else { null })
        it.setTimestamp(8, if (planlagtMeldingDbModel.sendt != null) { Timestamp.from(planlagtMeldingDbModel.sendt?.toInstant()) } else { null })
        it.setString(9, planlagtMeldingDbModel.jmsCorrelationId)
        it.execute()
    }
    this.commit()
}

fun Connection.hentPlanlagtMelding(fnr: String, startdato: LocalDate): List<PlanlagtMeldingDbModel> =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE fnr=? AND startdato=?;
            """
    ).use {
        it.setString(1, fnr)
        it.setObject(2, startdato)
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }
    }

fun Connection.hentPlanlagtMeldingMedId(id: UUID): PlanlagtMeldingDbModel? =
    this.prepareStatement(
        """
            SELECT * FROM planlagt_melding WHERE id=?;
            """
    ).use {
        it.setObject(1, id)
        it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
    }

fun Connection.hentUtbetaltEvent(fnr: String, startdato: LocalDate): List<UtbetaltEvent> =
    this.prepareStatement(
        """
            SELECT * FROM utbetaltevent WHERE fnr=? AND startdato=?;
            """
    ).use {
        it.setString(1, fnr)
        it.setObject(2, startdato)
        it.executeQuery().toList { toUtbetaltEvent() }
    }

fun ResultSet.toUtbetaltEvent(): UtbetaltEvent =
    UtbetaltEvent(
        utbetalteventid = getObject("utbetalteventid", UUID::class.java),
        fnr = getString("fnr"),
        aktorid = getString("aktorid"),
        sykmeldingid = getObject("sykmeldingid", UUID::class.java),
        startdato = getObject("startdato", LocalDate::class.java),
        organisasjonsnummer = getString("organisasjonsnummer"),
        hendelser = getHendelser(),
        oppdrag = getOppdrag(),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        forbrukteSykedager = getInt("forbrukte_sykedager"),
        gjenstaendeSykedager = getInt("gjenstaende_sykedager"),
        opprettet = getObject("opprettet", LocalDateTime::class.java)
    )

fun ResultSet.getHendelser(): Set<UUID> =
    objectMapper.readValue(getString("hendelser"))

fun ResultSet.getOppdrag(): List<Utbetalt> =
    objectMapper.readValue(getString("oppdrag"))
