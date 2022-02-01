package no.nav.syfo.aktivermelding

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.hentPlanlagtMeldingMedId
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.lagreUtbetaltEvent
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

object AktiverMeldingServiceTest : Spek({
    val testDb = TestDB.database
    val arenaMeldingService = mockk<ArenaMeldingService>()
    val smregisterClient = mockk<SmregisterClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val syfosyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val aktiverMeldingService = AktiverMeldingService(testDb, smregisterClient, arenaMeldingService, pdlPersonService, syfosyketilfelleClient)

    beforeEachTest {
        clearAllMocks()
        coEvery { pdlPersonService.isAlive(any(), any()) } returns true
        every { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) } returns "correlationId"
    }

    afterEachTest {
        testDb.connection.dropData()
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
            coEvery { pdlPersonService.isAlive("fnr", any()) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
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
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
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
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo null
        }
        it("Avbryter 8-ukersmelding hvis bruker er sykmeldt, men det er et nytt sykefravær og stansmelding har blitt vurdert (avbrutt)") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", any()) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr", any(), any()) } returns true
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = "fnr",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = AKTIVITETSKRAV_8_UKER_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "fnr",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = STANS_TYPE,
                    avbrutt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
            testDb.connection.lagreUtbetaltEvent("fnr", LocalDate.of(2020, 1, 10), "aktorId")

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { syfosyketilfelleClient.harSykeforlopMedNyereStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMeldingMedId(id)
            planlagtMelding!!.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
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
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
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
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo null
        }
        it("Avbryter 39-ukersmelding hvis bruker har et nyere sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = BREV_39_UKER_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id2,
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 5, 10),
                    type = BREV_39_UKER_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 1, 10)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        it("Sender 39-ukersmelding hvis bruker er sykmeldt og har et eldre sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = BREV_39_UKER_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id2,
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 5, 10),
                    type = BREV_39_UKER_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id2))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 5, 10)).first()
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        it("Avbryter 39-ukersmelding hvis bruker er sykmeldt, men det er et nytt sykefravær og stansmelding har blitt vurdert (sendt)") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr2", any(), any()) } returns true
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = BREV_39_UKER_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "fnr2",
                    startdato = LocalDate.of(2020, 1, 10),
                    type = STANS_TYPE,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )
            testDb.connection.lagreUtbetaltEvent("fnr2", LocalDate.of(2020, 1, 10), "aktorId2")

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { syfosyketilfelleClient.harSykeforlopMedNyereStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMeldingMedId(id)
            planlagtMelding!!.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
    }

    describe("Aktivering stansmelding") {
        val fnr = "15060188888"
        it("Avbryter stansmelding hvis bruker har et nyere sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 10),
                    type = BREV_4_UKER_TYPE,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20)
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 10),
                    type = STANS_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id2,
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 5, 10),
                    type = BREV_4_UKER_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 10)).find { it.type == STANS_TYPE }!!
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        it("Sender stansmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns null
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 14),
                    type = BREV_4_UKER_TYPE,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(20)
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 14),
                    type = STANS_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).find { it.type == STANS_TYPE }!!
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        it("Avbryter stansmelding hvis bruker ikke lenger er sykmeldt og det ikke er sendt 4-ukersmelding") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns null
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 14),
                    type = BREV_4_UKER_TYPE
                )
            )
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 14),
                    type = STANS_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).find { it.type == STANS_TYPE }!!
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        it("Utsetter stansmelding hvis bruker fortsatt er sykmeldt") {
            val id = UUID.randomUUID()
            val tom = LocalDate.of(2020, 3, 10)
            coEvery { smregisterClient.erSykmeldtTilOgMed(fnr, id) } returns tom
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    fnr = fnr,
                    startdato = LocalDate.of(2020, 1, 14),
                    type = STANS_TYPE
                )
            )

            runBlocking {
                aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))
            }

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.sendes shouldBeEqualTo tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding.jmsCorrelationId shouldBeEqualTo null
        }
    }
})
