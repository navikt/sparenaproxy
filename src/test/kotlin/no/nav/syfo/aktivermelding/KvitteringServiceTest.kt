package no.nav.syfo.aktivermelding

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertFailsWith
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMeldingMedId
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
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
        it("Feiler hvis kvitteringstatus ikke er ok") {
            assertFailsWith<RuntimeException> {
                kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil)
            }
        }

        it("Feiler ikke hvis planlagt melding allerede er registrert sendt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = "12345678910", sendt = OffsetDateTime.now(ZoneOffset.UTC)))

            kvitteringService.behandleKvittering(kvitteringsmelding)
        }

        it("Setter riktig planlagt melding til sendt") {
            val id = UUID.randomUUID()
            val idIkkeSendes = UUID.randomUUID()
            val idAnnenBruker = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = "12345678910"))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = idIkkeSendes, fnr = "12345678910", sendes = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1)))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = idAnnenBruker, fnr = "01987654321"))

            kvitteringService.behandleKvittering(kvitteringsmelding)

            val planlagtMeldingDbModel = testDb.connection.hentPlanlagtMeldingMedId(id)
            val planlagtMeldingDbModelIkkeSendes = testDb.connection.hentPlanlagtMeldingMedId(idIkkeSendes)
            val planlagtMeldingDbModelAnnenBruker = testDb.connection.hentPlanlagtMeldingMedId(idAnnenBruker)
            planlagtMeldingDbModel!!.sendt shouldNotEqual null
            planlagtMeldingDbModelIkkeSendes!!.sendt shouldEqual null
            planlagtMeldingDbModelAnnenBruker!!.sendt shouldEqual null
        }
    }
})
