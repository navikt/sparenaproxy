package no.nav.syfo.aktivermelding

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.mockk
import java.time.LocalDate
import no.nav.syfo.aktivermelding.kafka.MottattSykmeldingConsumer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
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

    beforeEachTest {
        clearAllMocks()
    }

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
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
