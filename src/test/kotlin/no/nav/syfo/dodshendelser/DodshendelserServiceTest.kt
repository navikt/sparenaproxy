package no.nav.syfo.dodshendelser

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.mockk
import java.time.Clock
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
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class DodshendelserServiceTest :
    FunSpec({
        val personhendelserConsumer = mockk<PersonhendelserConsumer>()
        val testDb = TestDB.database
        val dodshendelserService =
            DodshendelserService(
                ApplicationState(alive = true, ready = true),
                personhendelserConsumer,
                testDb
            )
        val avbruttTidspunkt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusDays(3)

        beforeTest {
            clearAllMocks()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "12345678910",
                    startdato = LocalDate.of(2020, 1, 25),
                    avbrutt = avbruttTidspunkt
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "12345678910",
                    startdato = LocalDate.of(2020, 2, 25),
                    sendt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusWeeks(3)
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "12345678910",
                    startdato = LocalDate.of(2020, 3, 25)
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "01987654321",
                    startdato = LocalDate.of(2020, 4, 25)
                )
            )
        }

        afterTest { testDb.connection.dropData() }

        context("Håndtering av dødsfall") {
            test("Avbryter kun planlagte meldinger for avdød bruker") {
                dodshendelserService.handleDodsfall(listOf("12345678910"))

                testDb.connection
                    .hentPlanlagtMelding("12345678910", LocalDate.of(2020, 1, 25))[0]
                    .avbrutt shouldBeEqualTo avbruttTidspunkt
                testDb.connection
                    .hentPlanlagtMelding("12345678910", LocalDate.of(2020, 2, 25))[0]
                    .avbrutt shouldBeEqualTo null
                testDb.connection
                    .hentPlanlagtMelding("12345678910", LocalDate.of(2020, 3, 25))[0]
                    .avbrutt shouldNotBeEqualTo null
                testDb.connection
                    .hentPlanlagtMelding("01987654321", LocalDate.of(2020, 4, 25))[0]
                    .avbrutt shouldBeEqualTo null
            }
            test("Avbryter planlagte meldinger for alle brukers identer for avdød bruker") {
                dodshendelserService.handleDodsfall(listOf("12345678910", "01987654321"))

                testDb.connection
                    .hentPlanlagtMelding("12345678910", LocalDate.of(2020, 3, 25))[0]
                    .avbrutt shouldNotBeEqualTo null
                testDb.connection
                    .hentPlanlagtMelding("01987654321", LocalDate.of(2020, 4, 25))[0]
                    .avbrutt shouldNotBeEqualTo null
            }
            test("Feiler ikke hvis personidenter ikke finnes") {
                dodshendelserService.handleDodsfall(listOf("000000111111"))
            }
        }
    })
