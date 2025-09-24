package no.nav.syfo.aktivermelding

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.aktivermelding.db.hentPlanlagtMelding
import no.nav.syfo.aktivermelding.db.sendPlanlagtMelding
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.AktivitetIkkeMulig
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.Gradert
import no.nav.syfo.model.MedisinskArsak
import no.nav.syfo.model.Periode
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import no.nav.syfo.testutil.opprettReceivedSykmelding
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldNotBeEqualTo

class MottattSykmeldingServiceTest :
    FunSpec(
        {
            val testDb = TestDB.database
            val arenaMeldingService = mockk<ArenaMeldingService>()
            val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
            val mottattSykmeldingService =
                MottattSykmeldingService(
                    testDb,
                    syfoSyketilfelleClient,
                )
            val idAvbrutt = UUID.randomUUID()
            val idAvbrutt2 = UUID.randomUUID()
            val idAvbrutt3 = UUID.randomUUID()
            val idIkkeAvbrutt = UUID.randomUUID()
            val idAvbruttStansmelding = UUID.randomUUID()
            val idStansmelding = UUID.randomUUID()
            val idStansmelding2 = UUID.randomUUID()
            val utsendingStansmelding =
                OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).plusDays(3)

            beforeTest {
                clearAllMocks()
                testDb.connection.dropData()
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idAvbrutt,
                        type = AKTIVITETSKRAV_8_UKER_TYPE,
                        fnr = "12345678910",
                        startdato = LocalDate.of(2020, 3, 25),
                        avbrutt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusDays(3),
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idStansmelding,
                        type = STANS_TYPE,
                        fnr = "12345678910",
                        startdato = LocalDate.of(2020, 3, 25),
                        sendes = utsendingStansmelding,
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idAvbrutt2,
                        type = AKTIVITETSKRAV_8_UKER_TYPE,
                        fnr = "12345678910",
                        startdato = LocalDate.of(2020, 1, 25),
                        avbrutt =
                            OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusWeeks(3),
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idIkkeAvbrutt,
                        type = AKTIVITETSKRAV_8_UKER_TYPE,
                        fnr = "01987654321",
                        startdato = LocalDate.of(2020, 3, 25),
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idAvbruttStansmelding,
                        type = STANS_TYPE,
                        fnr = "01987654321",
                        startdato = LocalDate.of(2020, 3, 25),
                        avbrutt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idAvbrutt3,
                        type = BREV_39_UKER_TYPE,
                        fnr = "11223344556",
                        startdato = LocalDate.of(2020, 3, 30),
                        avbrutt = OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)).minusDays(3),
                    ),
                )
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = idStansmelding2,
                        type = STANS_TYPE,
                        fnr = "11223344556",
                        startdato = LocalDate.of(2020, 3, 30),
                        sendes = utsendingStansmelding,
                    ),
                )
                every { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) } returns
                    "correlationId"
            }

            afterTest { testDb.connection.dropData() }

            context("Test av behandling av mottatt sykmelding") {
                test("Ignorerer sykmelding uten tilhørende planlagte meldinger") {
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "01987654321",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify(exactly = 0) {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                }
                test(
                    "Sender ikke avbrutt aktivitetskravmelding for gradert sykmelding, utsetter stansmelding",
                ) {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 3, 25)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "12345678910",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = Gradert(false, 60),
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify { syfoSyketilfelleClient.getStartDatoForSykmelding(any(), any()) }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val stansmelding = testDb.hentPlanlagtMelding(idStansmelding)
                    stansmelding?.sendes shouldBeEqualTo
                        LocalDate.now()
                            .plusWeeks(3)
                            .plusDays(17)
                            .atStartOfDay()
                            .atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toOffsetDateTime()
                }
                test("Ignorerer sykmelding som ikke har avbrutt melding for samme sykeforløp") {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 6, 25)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "12345678910",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify { syfoSyketilfelleClient.getStartDatoForSykmelding(any(), any()) }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val stansmelding = testDb.hentPlanlagtMelding(idStansmelding)
                    stansmelding?.sendes shouldBeEqualTo utsendingStansmelding
                }
                test(
                    "Oppdaterer ikke stansmelding hvis nytt utsendingstidspunkt er tidligere enn det som er satt",
                ) {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 3, 25)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "12345678910",
                            listOf(
                                Periode(
                                    fom = LocalDate.now().minusWeeks(3),
                                    tom = LocalDate.now().minusWeeks(2),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify { syfoSyketilfelleClient.getStartDatoForSykmelding(any(), any()) }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val stansmelding = testDb.hentPlanlagtMelding(idStansmelding)
                    stansmelding?.sendes shouldBeEqualTo utsendingStansmelding
                }
                test(
                    "Oppdaterer og sender tidligere avbrutt melding for samme sykeforløp hvis sykmelding ikke er gradert, utsetter stansmelding",
                ) {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 3, 25)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "12345678910",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify { syfoSyketilfelleClient.getStartDatoForSykmelding(any(), any()) }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val meldinger =
                        testDb.connection.hentPlanlagtMelding(
                            "12345678910",
                            LocalDate.of(2020, 3, 25),
                        )
                    meldinger.size shouldBeEqualTo 2
                    val planlagtMelding8uker =
                        meldinger.find { it.type == AKTIVITETSKRAV_8_UKER_TYPE }
                    val planlagtStansmelding = meldinger.find { it.type == STANS_TYPE }
                    planlagtMelding8uker!!.avbrutt shouldNotBeEqualTo null
                    planlagtStansmelding?.sendes shouldBeEqualTo
                        LocalDate.now()
                            .plusWeeks(3)
                            .plusDays(17)
                            .atStartOfDay()
                            .atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toOffsetDateTime()
                    planlagtMelding8uker.jmsCorrelationId shouldBeEqualTo null
                }
                test("Skal ikke sende ny 8-ukersmelding hvis melding er sent før") {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 3, 25)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "12345678910",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)
                    testDb.sendPlanlagtMelding(
                        idAvbrutt,
                        OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC)),
                        "correlationId",
                    )
                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify(exactly = 2) {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val meldinger =
                        testDb.connection.hentPlanlagtMelding(
                            "12345678910",
                            LocalDate.of(2020, 3, 25),
                        )
                    meldinger.size shouldBeEqualTo 2
                    val planlagtMelding8uker =
                        meldinger.find { it.type == AKTIVITETSKRAV_8_UKER_TYPE }
                    planlagtMelding8uker!!.avbrutt shouldNotBeEqualTo null
                }
                test(
                    "Ignorerer sykmelding som ikke har avbrutt 39-ukersmelding for samme sykeforløp"
                ) {
                    coEvery {
                        syfoSyketilfelleClient.getStartDatoForSykmelding(
                            any(),
                            any(),
                        )
                    } returns LocalDate.of(2020, 6, 30)
                    val receivedSykmelding =
                        opprettReceivedSykmelding(
                            "11223344556",
                            listOf(
                                Periode(
                                    fom = LocalDate.now(),
                                    tom = LocalDate.now().plusWeeks(3),
                                    aktivitetIkkeMulig =
                                        AktivitetIkkeMulig(
                                            medisinskArsak =
                                                MedisinskArsak(
                                                    null,
                                                    emptyList(),
                                                ),
                                            arbeidsrelatertArsak = null,
                                        ),
                                    avventendeInnspillTilArbeidsgiver = null,
                                    behandlingsdager = null,
                                    gradert = null,
                                    reisetilskudd = false,
                                ),
                            ),
                        )

                    mottattSykmeldingService.behandleMottattSykmelding(receivedSykmelding)

                    coVerify { syfoSyketilfelleClient.getStartDatoForSykmelding(any(), any()) }
                    coVerify(exactly = 0) { arenaMeldingService.sendPlanlagtMeldingTilArena(any()) }
                    val stansmelding = testDb.hentPlanlagtMelding(idStansmelding2)
                    stansmelding?.sendes shouldBeEqualTo utsendingStansmelding
                }
            }
        },
    )
