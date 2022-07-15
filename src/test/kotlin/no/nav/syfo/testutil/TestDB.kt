package no.nav.syfo.testutil

import io.mockk.every
import io.mockk.mockk
import no.nav.syfo.Environment
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.db.toList
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.toPlanlagtMeldingDbModel
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

class PsqlContainer : PostgreSQLContainer<PsqlContainer>("postgres:12")

class TestDB private constructor() {
    companion object {
        var database: DatabaseInterface
        val env = mockk<Environment>()
        val psqlContainer: PsqlContainer = PsqlContainer()
            .withExposedPorts(5432)
            .withUsername("username")
            .withPassword("password")
            .withDatabaseName("database")
            .withInitScript("db/dbinit-test.sql")

        init {
            psqlContainer.start()
            every { env.databaseUsername } returns "username"
            every { env.databasePassword } returns "password"
            every { env.dbName } returns "database"
            every { env.jdbcUrl() } returns psqlContainer.jdbcUrl
            try {
                database = Database(env)
            } catch (e: Exception) {
                database = Database(env)
            }
        }
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
    use { connection ->
        connection.prepareStatement(
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
        connection.commit()
    }
}

fun Connection.hentPlanlagtMelding(fnr: String, startdato: LocalDate): List<PlanlagtMeldingDbModel> =
    use { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND startdato=?;
            """
        ).use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }
        }
    }

fun Connection.hentPlanlagtMeldingMedId(id: UUID): PlanlagtMeldingDbModel? =
    use { connection ->
        connection.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE id=?;
            """
        ).use {
            it.setObject(1, id)
            it.executeQuery().toList { toPlanlagtMeldingDbModel() }.firstOrNull()
        }
    }

fun Connection.hentUtbetaltEvent(fnr: String, startdato: LocalDate): List<UtbetaltEvent> =
    use {
        it.prepareStatement(
            """
            SELECT * FROM utbetaltevent WHERE fnr=? AND startdato=?;
            """
        ).use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().toList { toUtbetaltEvent() }
        }
    }

fun ResultSet.toUtbetaltEvent(): UtbetaltEvent =
    UtbetaltEvent(
        utbetalteventid = getObject("utbetalteventid", UUID::class.java),
        fnr = getString("fnr"),
        aktorid = getString("aktorid"),
        startdato = getObject("startdato", LocalDate::class.java),
        organisasjonsnummer = getString("organisasjonsnummer"),
        fom = getObject("fom", LocalDate::class.java),
        tom = getObject("tom", LocalDate::class.java),
        forbrukteSykedager = getInt("forbrukte_sykedager"),
        gjenstaendeSykedager = getInt("gjenstaende_sykedager"),
        opprettet = getObject("opprettet", LocalDateTime::class.java),
        maksdato = getObject("maksdato", LocalDate::class.java),
        utbetalingId = getObject("utbetalingid", UUID::class.java)
    )

fun Connection.lagreUtbetaltEvent(fnr: String, startdato: LocalDate, aktorId: String) {
    use {
        it.prepareStatement(
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
        ).use {
            it.setObject(1, UUID.randomUUID())
            it.setObject(2, startdato)
            it.setString(3, aktorId)
            it.setString(4, fnr)
            it.setString(5, "9090880")
            it.setObject(6, LocalDate.now().minusMonths(1))
            it.setObject(7, LocalDate.now().minusDays(10))
            it.setInt(8, 20)
            it.setInt(9, 250)
            it.setTimestamp(10, Timestamp.valueOf(LocalDateTime.now(Clock.tickMillis(ZoneId.systemDefault()))))
            it.setObject(11, LocalDate.now().plusDays(250))
            it.setObject(12, UUID.randomUUID())
            it.execute()
        }
        it.commit()
    }
}
