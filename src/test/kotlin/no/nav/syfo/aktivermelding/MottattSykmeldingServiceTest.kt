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
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivermelding.kafka.MottattSykmeldingConsumer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import no.nav.syfo.testutil.opprettReceivedSykmelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object MottattSykmeldingServiceTest : Spek({
    val testDb = TestDB()
    val mottattSykmeldingConsumer = mockk<MottattSykmeldingConsumer>()
    val arenaMeldingService = mockk<ArenaMeldingService>(relaxed = true)
    val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val mottattSykmeldingService = MottattSykmeldingService(ApplicationState(alive = true, ready = true), mottattSykmeldingConsumer, testDb, syfoSyketilfelleClient, arenaMeldingService)
    val idAvbrutt = UUID.randomUUID()
    val idAvbrutt2 = UUID.randomUUID()
    val idIkkeAvbrutt = UUID.randomUUID()

    beforeEachTest {
        clearAllMocks()
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = idAvbrutt, fnr = "12345678910", startdato = LocalDate.of(2020, 3, 25), avbrutt = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3)))
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = idAvbrutt2, fnr = "12345678910", startdato = LocalDate.of(2020, 1, 25), avbrutt = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(3)))
        testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = idIkkeAvbrutt, fnr = "01987654321", startdato = LocalDate.of(2020, 3, 25)))
    }

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test av behandling av mottatt sykmelding") {
        it("Ignorerer sykmelding med gradert periode") {
            val receivedSykmelding = opprettReceivedSykmelding(
                "12345678910", listOf(
                    Periode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusWeeks(3),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = Gradert(false, 60),
                        reisetilskudd = false
                    )
                )
            )

            runBlocking {
                mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)
            }

            coVerify(exactly = 0) { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Ignorerer sykmelding som ikke har avbrutt melding for samme sykeforløp") {
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) } returns LocalDate.of(2020, 6, 25)
            val receivedSykmelding = opprettReceivedSykmelding(
                "12345678910", listOf(
                    Periode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusWeeks(3),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false
                    )
                )
            )

            runBlocking {
                mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)
            }

            coVerify { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) }
            coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
        }
        it("Oppretter og sender ny melding hvis avbrutt melding for samme sykeforløp og sykmelding ikke er gradert") {
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) } returns LocalDate.of(2020, 3, 25)
            val receivedSykmelding = opprettReceivedSykmelding(
                "12345678910", listOf(
                    Periode(
                        fom = LocalDate.now(),
                        tom = LocalDate.now().plusWeeks(3),
                        aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                        avventendeInnspillTilArbeidsgiver = null,
                        behandlingsdager = null,
                        gradert = null,
                        reisetilskudd = false
                    )
                )
            )

            runBlocking {
                mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)
            }

            coVerify { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) }
            coVerify { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
            val meldinger = testDb.connection.hentPlanlagtMelding("12345678910", LocalDate.of(2020, 3, 25))
            meldinger.size shouldEqual 2
        }
    }

    describe("Tester for å finne avbrutt melding") {
        it("Returnerer null hvis det ikke finnes avbrutt melding for fnr") {
            var melding: PlanlagtMeldingDbModel? = null
            runBlocking {
                melding = mottattSykmeldingService.finnAvbruttMeldingForSykeforloep("01987654321", "aktorId", UUID.randomUUID().toString())
            }

            melding shouldEqual null
        }
        it("Returnerer null hvis det finnes avbrutt melding for fnr for annen startdato") {
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) } returns LocalDate.of(2020, 6, 25)

            var melding: PlanlagtMeldingDbModel? = null
            runBlocking {
                melding = mottattSykmeldingService.finnAvbruttMeldingForSykeforloep("12345678910", "aktorId", UUID.randomUUID().toString())
            }

            melding shouldEqual null
        }
        it("Returnerer melding hvis det finnes avbrutt melding for fnr og samme startdato") {
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) } returns LocalDate.of(2020, 3, 25)

            var melding: PlanlagtMeldingDbModel? = null
            runBlocking {
                melding = mottattSykmeldingService.finnAvbruttMeldingForSykeforloep("12345678910", "aktorId", UUID.randomUUID().toString())
            }

            melding?.id shouldEqual idAvbrutt
        }
        it("Kaster feil hvis sykeforløp ikke finnes (syfosyketilfelle ikke oppdatert)") {
            coEvery { syfoSyketilfelleClient.finnStartdato(any(), any(), any()) } throws RuntimeException("Fant ikke sykeforløp")

            assertFailsWith<RuntimeException> {
                runBlocking {
                    mottattSykmeldingService.finnAvbruttMeldingForSykeforloep("12345678910", "aktorId", UUID.randomUUID().toString())
                }
            }
        }
    }

    describe("Test av logikk for om sykmelding er gradert") {
        it("Sykmelding uten gradert periode inneholder ikke gradert periode") {
            val perioder: List<Periode> = listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusWeeks(3),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false
                )
            )

            val inneholderGradertPeriode = mottattSykmeldingService.inneholderGradertPeriode(perioder)

            inneholderGradertPeriode shouldEqual false
        }
        it("Sykmelding med gradert periode inneholder gradert periode") {
            val perioder: List<Periode> = listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusWeeks(3),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = Gradert(false, 60),
                    reisetilskudd = false
                )
            )

            val inneholderGradertPeriode = mottattSykmeldingService.inneholderGradertPeriode(perioder)

            inneholderGradertPeriode shouldEqual true
        }
        it("Sykmelding med flere perioder inneholder gradert periode hvis en av periodene er gradert") {
            val perioder: List<Periode> = listOf(
                Periode(
                    fom = LocalDate.now(),
                    tom = LocalDate.now().plusWeeks(1),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = null,
                    reisetilskudd = false
                ),
                Periode(
                    fom = LocalDate.now().plusWeeks(1),
                    tom = LocalDate.now().plusWeeks(2),
                    aktivitetIkkeMulig = AktivitetIkkeMulig(medisinskArsak = MedisinskArsak(null, emptyList()), arbeidsrelatertArsak = null),
                    avventendeInnspillTilArbeidsgiver = null,
                    behandlingsdager = null,
                    gradert = Gradert(false, 60),
                    reisetilskudd = false
                )
            )

            val inneholderGradertPeriode = mottattSykmeldingService.inneholderGradertPeriode(perioder)

            inneholderGradertPeriode shouldEqual true
        }
    }
})
