package no.nav.syfo.aktivermelding

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldEqual
import org.amshove.kluent.shouldNotEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object AktiverMeldingServiceTest : Spek({
    val testDb = TestDB()
    val arenaMeldingService = mockk<ArenaMeldingService>(relaxed = true)
    val smregisterClient = mockk<SmregisterClient>()
    val aktiverMeldingService = AktiverMeldingService(testDb, smregisterClient, arenaMeldingService)

    beforeEachTest {
        clearAllMocks()
    }

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test av behandleAktiverMelding") {
        it("Ignorerer melding som er avbrutt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, avbrutt = OffsetDateTime.now(ZoneOffset.UTC)))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Ignorerer melding som er sendt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, sendt = OffsetDateTime.now(ZoneOffset.UTC)))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Sender 8-ukersmelding, men oppdaterer ikke i db hvis bruker fortsatt er 100% sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns true
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldEqual null
            planlagtMelding.avbrutt shouldEqual null
        }
        it("Avbryter 8-ukersmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotEqual null
            planlagtMelding.sendt shouldEqual null
        }
        it("Sender 39-ukersmelding, men oppdaterer ikke i db hvis bruker fortsatt er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr", id) } returns true
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, type = BREV_39_UKER_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldEqual null
            planlagtMelding.avbrutt shouldEqual null
        }
        it("Avbryter 39-ukersmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr", id) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, type = BREV_39_UKER_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotEqual null
            planlagtMelding.sendt shouldEqual null
        }
    }
})
