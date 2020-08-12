package no.nav.syfo.dodshendelser

import io.mockk.clearAllMocks
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.dodshendelser.kafka.PersonhendelserConsumer
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DodshendelserServiceTest : Spek({
    val personhendelserConsumer = mockk<PersonhendelserConsumer>()
    val testDb = TestDB()
    val dodshendelserService = DodshendelserService(ApplicationState(alive = true, ready = true), personhendelserConsumer, testDb)
    val avbruttTidspunkt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3)

    beforeEachTest {
        clearAllMocks()
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = "12345678910", startdato = LocalDate.of(2020, 1, 25), avbrutt = avbruttTidspunkt))
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = "12345678910", startdato = LocalDate.of(2020, 2, 25), sendt = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(3)))
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = "12345678910", startdato = LocalDate.of(2020, 3, 25)))
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = "01987654321", startdato = LocalDate.of(2020, 4, 25)))
    }

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Håndtering av dødsfall") {
        it("Avbryter kun planlagte meldinger for avdød bruker") {
            dodshendelserService.handleDodsfall(listOf("12345678910"))

            testDb.connection.hentPlanlagtMelding("12345678910", LocalDate.of(2020, 1, 25))[0].avbrutt shouldEqual avbruttTidspunkt
            testDb.connection.hentPlanlagtMelding("12345678910", LocalDate.of(2020, 2, 25))[0].avbrutt shouldEqual null
            testDb.connection.hentPlanlagtMelding("12345678910", LocalDate.of(2020, 3, 25))[0].avbrutt shouldNotEqual null
            testDb.connection.hentPlanlagtMelding("01987654321", LocalDate.of(2020, 4, 25))[0].avbrutt shouldEqual null
        }
        it("Avbryter planlagte meldinger for alle brukers identer for avdød bruker") {
            dodshendelserService.handleDodsfall(listOf("12345678910", "01987654321"))

            testDb.connection.hentPlanlagtMelding("12345678910", LocalDate.of(2020, 3, 25))[0].avbrutt shouldNotEqual null
            testDb.connection.hentPlanlagtMelding("01987654321", LocalDate.of(2020, 4, 25))[0].avbrutt shouldNotEqual null
        }
        it("Feiler ikke hvis personidenter ikke finnes") {
            dodshendelserService.handleDodsfall(listOf("000000111111"))
        }
    }
})
