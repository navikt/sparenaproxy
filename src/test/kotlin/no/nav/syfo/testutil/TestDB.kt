package no.nav.syfo.testutil

import com.fasterxml.jackson.module.kotlin.readValue
import com.opentable.db.postgres.embedded.EmbeddedPostgres
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.lagrevedtak.Utbetalt
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.lagrevedtak.db.PlanlagtMeldingDbModel
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

fun ResultSet.toPlanlagtMeldingDbModel(): PlanlagtMeldingDbModel =
    PlanlagtMeldingDbModel(
        id = getObject("id", UUID::class.java),
        fnr = getString("fnr"),
        startdato = getObject("startdato", LocalDate::class.java),
        type = getString("type"),
        opprettet = getTimestamp("opprettet").toInstant().atOffset(ZoneOffset.UTC),
        sendes = getTimestamp("sendes").toInstant().atOffset(ZoneOffset.UTC),
        avbrutt = getTimestamp("avbrutt")?.toInstant()?.atOffset(ZoneOffset.UTC),
        sendt = getTimestamp("sendt")?.toInstant()?.atOffset(ZoneOffset.UTC)
    )

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
