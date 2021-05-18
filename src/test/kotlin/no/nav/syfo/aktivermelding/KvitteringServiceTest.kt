package no.nav.syfo.aktivermelding

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KvitteringServiceTest : Spek({
    val kvitteringsmelding = "K278M890Kvittering Arena002270307202010070612345678910J                                                                                                                                                                            "
    val kvitteringsmeldingMedFeil = "K278M890Kvittering Arena002270307202010070612345678910NXXXXXXXXFeilmelding                                                                                                                                                         "

    val testDb = TestDB()
    val kvitteringService = KvitteringService(testDb)

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test av behandleKvittering") {
        it("Feiler hvis kvitteringstatus ikke er ok og melding ikke finnes i database") {
            assertFailsWith<RuntimeException> {
                kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil, UUID.randomUUID().toString())
            }
        }
        it("Feiler hvis kvitteringstatus ikke er ok og correlationId ikke er en UUID") {
            assertFailsWith<RuntimeException> {
                kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil, "id")
            }
        }
        it("Prøver å resende hvis kvitteringstatus ikke er ok og melding finnes i database") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )

            kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil, id.toString())
        }
        it("Feiler ikke hvis kvitteringsstatus er ok") {
            kvitteringService.behandleKvittering(kvitteringsmelding, "id")
        }
    }
})
