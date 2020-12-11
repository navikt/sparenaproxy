package no.nav.syfo.aktivermelding

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.pdl.service.PdlPersonService
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
    val pdlPersonService = mockk<PdlPersonService>()
    val aktiverMeldingService = AktiverMeldingService(testDb, smregisterClient, arenaMeldingService, pdlPersonService)

    beforeEachTest {
        clearAllMocks()
        coEvery { pdlPersonService.erPersonDod(any(), any()) } returns false
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
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    avbrutt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Ignorerer melding som er sendt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Avbryter melding hvis bruker er død") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns true
            coEvery { pdlPersonService.erPersonDod("fnr", any()) } returns true
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
    }

    describe("Aktivering 8-ukersmelding") {
        it("Sender 8-ukersmelding og oppdaterer i db hvis bruker fortsatt er 100% sykmeldt") {
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
            planlagtMelding.sendt shouldNotEqual null
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
    }

    describe("Aktivering 39-ukersmelding") {
        it("Sender 39-ukersmelding og oppdaterer i db hvis bruker fortsatt er sykmeldt") {
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
            planlagtMelding.sendt shouldNotEqual null
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
        it("Avbryter 39-ukersmelding hvis bruker har et nyere sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = "fnr2", startdato = LocalDate.of(2020, 1, 10), type = BREV_39_UKER_TYPE))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id2, fnr = "fnr2", startdato = LocalDate.of(2020, 5, 10), type = BREV_39_UKER_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 1, 10)).first()
            planlagtMelding.avbrutt shouldNotEqual null
            planlagtMelding.sendt shouldEqual null
        }
        it("Sender 39-ukersmelding hvis bruker er sykmeldt og har et eldre sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = "fnr2", startdato = LocalDate.of(2020, 1, 10), type = BREV_39_UKER_TYPE))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id2, fnr = "fnr2", startdato = LocalDate.of(2020, 5, 10), type = BREV_39_UKER_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id2))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 5, 10)).first()
            planlagtMelding.sendt shouldNotEqual null
            planlagtMelding.avbrutt shouldEqual null
        }
    }

    describe("Aktivering stansmelding") {
        val fnr = "15060188888"
        it("Avbryter stansmelding hvis bruker har et nyere sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = fnr, startdato = LocalDate.of(2020, 1, 10), type = BREV_4_UKER_TYPE, sendt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20)))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = fnr, startdato = LocalDate.of(2020, 1, 10), type = STANS_TYPE))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id2, fnr = fnr, startdato = LocalDate.of(2020, 5, 10), type = BREV_4_UKER_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 10)).first()
            planlagtMelding.avbrutt shouldNotEqual null
            planlagtMelding.sendt shouldEqual null
        }
        it("Sender stansmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns null
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = fnr, startdato = LocalDate.of(2020, 1, 14), type = BREV_4_UKER_TYPE, sendt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20)))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = fnr, startdato = LocalDate.of(2020, 1, 14), type = STANS_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldNotEqual null
            planlagtMelding.avbrutt shouldEqual null
        }
        it("Avbryter stansmelding hvis bruker ikke lenger er sykmeldt og det ikke er sendt 4-ukersmelding") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns null
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = fnr, startdato = LocalDate.of(2020, 1, 14), type = BREV_4_UKER_TYPE))
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = fnr, startdato = LocalDate.of(2020, 1, 14), type = STANS_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotEqual null
            planlagtMelding.sendt shouldEqual null
        }
        it("Utsetter stansmelding hvis bruker fortsatt er sykmeldt") {
            val id = UUID.randomUUID()
            val tom = LocalDate.of(2020, 3, 10)
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns tom
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, fnr = fnr, startdato = LocalDate.of(2020, 1, 14), type = STANS_TYPE))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldEqual null
            planlagtMelding.avbrutt shouldEqual null
            planlagtMelding.sendes shouldEqual tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }
    }
})
