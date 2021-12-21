package no.nav.syfo.lagrevedtak

import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEventOgPlanlagtMelding
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.hentUtbetaltEvent
import no.nav.syfo.testutil.lagUtbetaltEvent
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.Clock
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

object LagreUtbetaltEventOgPlanlagtMeldingServiceTest : Spek({
    val testDb = TestDB.database
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(testDb)

    val utbetaltEventId = UUID.fromString("58ac4866-5944-48a1-99fa-86d6f9f3103c")
    val sykmeldingId = UUID.fromString("f9e3db67-2913-40c7-bb68-9a701d9fd4f4")

    afterEachTest {
        testDb.connection.dropData()
    }

    describe("Test av lagring av vedtaksinfo og planlagte meldinger") {
        val startdato = LocalDate.of(2020, 6, 1)
        val tom = LocalDate.of(2020, 7, 15)
        it("Lagrer vedtak, stansmelding og planlagt melding 4, 8, 39 uker for nytt tilfelle") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            utbetaltEventFraDbListe.size shouldBeEqualTo 1
            val planlagtMelding4uker = planlagtMeldingFraDbListe.find { it.type == BREV_4_UKER_TYPE }
            val planlagtMelding8uker = planlagtMeldingFraDbListe.find { it.type == AKTIVITETSKRAV_8_UKER_TYPE }
            val planlagtMelding39uker = planlagtMeldingFraDbListe.find { it.type == BREV_39_UKER_TYPE }
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            val utbetaltEventFraDb = utbetaltEventFraDbListe.first()

            planlagtMelding4uker?.fnr shouldBeEqualTo "fnr"
            planlagtMelding4uker?.startdato shouldBeEqualTo startdato
            planlagtMelding4uker?.type shouldBeEqualTo BREV_4_UKER_TYPE
            planlagtMelding4uker?.sendes shouldBeEqualTo startdato.plusWeeks(4).atStartOfDay()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding4uker?.sendt shouldBeEqualTo null
            planlagtMelding4uker?.avbrutt shouldBeEqualTo null

            planlagtMelding8uker?.fnr shouldBeEqualTo "fnr"
            planlagtMelding8uker?.startdato shouldBeEqualTo startdato
            planlagtMelding8uker?.type shouldBeEqualTo AKTIVITETSKRAV_8_UKER_TYPE
            planlagtMelding8uker?.sendes shouldBeEqualTo startdato.plusWeeks(8).atStartOfDay()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding8uker?.sendt shouldBeEqualTo null
            planlagtMelding8uker?.avbrutt shouldBeEqualTo null

            planlagtMelding39uker?.fnr shouldBeEqualTo "fnr"
            planlagtMelding39uker?.startdato shouldBeEqualTo startdato
            planlagtMelding39uker?.type shouldBeEqualTo BREV_39_UKER_TYPE
            planlagtMelding39uker?.sendes shouldBeEqualTo startdato.plusWeeks(39).atStartOfDay()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding39uker?.sendt shouldBeEqualTo null
            planlagtMelding39uker?.avbrutt shouldBeEqualTo null

            planlagtStansmelding?.fnr shouldBeEqualTo "fnr"
            planlagtStansmelding?.startdato shouldBeEqualTo startdato
            planlagtStansmelding?.type shouldBeEqualTo STANS_TYPE
            planlagtStansmelding?.sendes shouldBeEqualTo tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtStansmelding?.sendt shouldBeEqualTo null
            planlagtStansmelding?.avbrutt shouldBeEqualTo null

            utbetaltEventFraDb shouldBeEqualTo utbetaltEvent
        }
        it("39-ukersmelding sendes umiddelbart for nytt tilfelle hvis antall gjenstående sykedager er mindre enn 66") {
            val utbetaltEvent =
                lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom, gjenstaendeSykedager = 60)

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            val planlagtMelding39uker = planlagtMeldingFraDbListe.find { it.type == BREV_39_UKER_TYPE }

            planlagtMelding39uker?.fnr shouldBeEqualTo "fnr"
            planlagtMelding39uker?.startdato shouldBeEqualTo startdato
            planlagtMelding39uker?.type shouldBeEqualTo BREV_39_UKER_TYPE
            planlagtMelding39uker?.sendes?.toLocalDate() shouldBeEqualTo OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
                .toLocalDate()
            planlagtMelding39uker?.sendt shouldBeEqualTo null
            planlagtMelding39uker?.avbrutt shouldBeEqualTo null
        }
        it("Lagrer kun vedtak og oppdaterer stansmelding, hvis planlagte meldinger finnes for syketilfellet fra før") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            val nesteUtbetaltEvent =
                lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), startdato, "fnr", tom.plusWeeks(1))

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            utbetaltEventFraDbListe.size shouldBeEqualTo 2
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldBeEqualTo tom.plusWeeks(1).plusDays(17).atStartOfDay()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }
        it("Oppdaterer 39-ukersmelding hvis finnes og ikke sendt for syketilfellet fra før og antall gjenstående sykedager er mindre enn 66") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            val nesteUtbetaltEvent =
                lagUtbetaltEvent(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    startdato,
                    "fnr",
                    tom.plusWeeks(1),
                    gjenstaendeSykedager = 60
                )

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            utbetaltEventFraDbListe.size shouldBeEqualTo 2
            val planlagt39ukersmelding = planlagtMeldingFraDbListe.find { it.type == BREV_39_UKER_TYPE }
            planlagt39ukersmelding?.sendes?.toLocalDate() shouldBeEqualTo OffsetDateTime.now(Clock.tickMillis(ZoneOffset.UTC))
                .toLocalDate()
        }
        it("Oppdaterer ikke stansmelding hvis nytt utsendingstidspunkt er tidligere enn det forrige") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            val nesteUtbetaltEvent =
                lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), startdato, "fnr", tom.minusWeeks(1))

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldBeEqualTo tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }
        it("Gjenåpner stansmelding hvis stansmelding for samme sykefravær var avbrutt") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            val nesteUtbetaltEvent =
                lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), startdato, "fnr", tom.plusWeeks(1))
            testDb.lagreUtbetaltEventOgPlanlagtMelding(
                utbetaltEvent,
                listOf(
                    PlanlagtMeldingDbModel(
                        id = UUID.randomUUID(),
                        fnr = "fnr",
                        startdato = startdato,
                        type = STANS_TYPE,
                        opprettet = tom.minusWeeks(10).atStartOfDay().atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                        sendes = tom.minusWeeks(8).atStartOfDay().atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                        avbrutt = tom.minusWeeks(8).atStartOfDay().atZone(ZoneId.systemDefault())
                            .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime(),
                        sendt = null
                    )
                )
            )
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 1
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldBeEqualTo tom.plusWeeks(1).plusDays(17).atStartOfDay()
                .atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtStansmelding?.avbrutt shouldBeEqualTo null
        }
        it("Oppretter stansmelding hvis det ikke finnes fra før") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            testDb.connection.lagrePlanlagtMelding(
                opprettPlanlagtMelding(
                    id = UUID.randomUUID(),
                    fnr = "fnr",
                    startdato = startdato
                )
            )

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 2
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldBeEqualTo tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault())
                .withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }
        it("Lagrer vedtak og melding hvis planlagt melding finnes for tidligere syketilfelle for samme bruker") {
            val nesteStartdato = startdato.plusMonths(1)
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr")
            val nesteUtbetaltEvent = lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), nesteStartdato, "fnr")

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val nestePlanlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", nesteStartdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            val nesteUtbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", nesteStartdato)
            planlagtMeldingFraDbListe.size shouldBeEqualTo 4
            nestePlanlagtMeldingFraDbListe.size shouldBeEqualTo 4
            utbetaltEventFraDbListe.size shouldBeEqualTo 1
            nesteUtbetaltEventFraDbListe.size shouldBeEqualTo 1
        }
    }
})
