package no.nav.syfo.aktivermelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class AktiverMeldingServiceTest : FunSpec({
    val testDb = TestDB.database
    val arenaMeldingService = mockk<ArenaMeldingService>()
    val smregisterClient = mockk<SmregisterClient>()
    val pdlPersonService = mockk<PdlPersonService>()
    val syfosyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val aktiverMeldingService = AktiverMeldingService(testDb, smregisterClient, arenaMeldingService, pdlPersonService, syfosyketilfelleClient)

    beforeTest {
        clearAllMocks()
        coEvery { pdlPersonService.isAlive(any(), any()) } returns true
        every { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) } returns "correlationId"
    }

    afterTest {
        testDb.connection.dropData()
    }
    context("Test av behandleAktiverMelding") {
        test("Ignorerer melding som er avbrutt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    avbrutt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        test("Ignorerer melding som er sendt") {
            val id = UUID.randomUUID()
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = id,
                    sendt = OffsetDateTime.now(ZoneOffset.UTC)
                )
            )

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        test("Avbryter melding hvis bruker er død") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr", any(), any()) } returns false
            coEvery { pdlPersonService.isAlive("fnr", any()) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
    }

    context("Aktivering 8-ukersmelding") {
        test("Sender 8-ukersmelding og oppdaterer i db hvis bruker fortsatt er 100% sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr", any(), any()) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        test("Avbryter 8-ukersmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.er100ProsentSykmeldt("fnr", id) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id))

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo null
        }
        test("Avbryter 8-ukersmelding hvis bruker er sykmeldt, men det er et nytt sykefravær og stansmelding har blitt vurdert (avbrutt)") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { syfosyketilfelleClient.harSykeforlopMedNyereStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMeldingMedId(id)
            planlagtMelding!!.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        test("Avbryter 8-ukersmelding hvis bruker er sykmeldt, men det er et nytt sykefravær også når stansmelding ikke er vurdert") {
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
                    avbrutt = null
                )
            )
            testDb.connection.lagreUtbetaltEvent("fnr", LocalDate.of(2020, 1, 10), "aktorId")

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { syfosyketilfelleClient.harSykeforlopMedNyereStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMeldingMedId(id)
            planlagtMelding!!.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
    }

    context("Aktivering 39-ukersmelding") {
        test("Sender 39-ukersmelding og oppdaterer i db hvis bruker fortsatt er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr", id) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr", any(), any()) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, type = BREV_39_UKER_TYPE))

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        test("Avbryter 39-ukersmelding hvis bruker ikke lenger er sykmeldt") {
            val id = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr", id) } returns false
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = id, type = BREV_39_UKER_TYPE))

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr", LocalDate.of(2020, 1, 14)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo null
        }
        test("Avbryter 39-ukersmelding hvis bruker har et nyere sykefravær") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 1, 10)).first()
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        test("Sender 39-ukersmelding hvis bruker er sykmeldt og har et eldre sykefravær") {
            val id = UUID.randomUUID()
            val id2 = UUID.randomUUID()
            coEvery { smregisterClient.erSykmeldt("fnr2", any()) } returns true
            coEvery { syfosyketilfelleClient.harSykeforlopMedNyereStartdato("fnr2", any(), any()) } returns false
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id2))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding("fnr2", LocalDate.of(2020, 5, 10)).first()
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        test("Avbryter 39-ukersmelding hvis bruker er sykmeldt, men det er et nytt sykefravær og stansmelding har blitt vurdert (sendt)") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { syfosyketilfelleClient.harSykeforlopMedNyereStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMeldingMedId(id)
            planlagtMelding!!.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
    }

    context("Aktivering stansmelding") {
        val fnr = "15060188888"
        test("Avbryter stansmelding hvis bruker har et nyere sykefravær") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 10)).find { it.type == STANS_TYPE }!!
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        test("Sender stansmelding hvis bruker ikke lenger er sykmeldt") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).find { it.type == STANS_TYPE }!!
            planlagtMelding.sendt shouldNotBeEqualTo null
            planlagtMelding.avbrutt shouldBeEqualTo null
            planlagtMelding.jmsCorrelationId shouldBeEqualTo "correlationId"
        }
        test("Avbryter stansmelding hvis bruker ikke lenger er sykmeldt og det ikke er sendt 4-ukersmelding") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

            coVerify(exactly = 0) { smregisterClient.er100ProsentSykmeldt(any(), any()) }
            coVerify(exactly = 0) { smregisterClient.erSykmeldt(any(), any()) }
            coVerify { smregisterClient.erSykmeldtTilOgMed(any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val planlagtMelding = testDb.connection.hentPlanlagtMelding(fnr, LocalDate.of(2020, 1, 14)).find { it.type == STANS_TYPE }!!
            planlagtMelding.avbrutt shouldNotBeEqualTo null
            planlagtMelding.sendt shouldBeEqualTo null
        }
        test("Utsetter stansmelding hvis bruker fortsatt er sykmeldt") {
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

            aktiverMeldingService.behandleAktiverMelding(AktiverMelding(id))

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
