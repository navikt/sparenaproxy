package no.nav.syfo.lagrevedtak

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.hentUtbetaltEvent
import no.nav.syfo.testutil.lagUtbetaltEvent
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object LagreUtbetaltEventOgPlanlagtMeldingServiceTest : Spek({
    val testDb = TestDB()
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(testDb)

    val utbetaltEventId = UUID.fromString("58ac4866-5944-48a1-99fa-86d6f9f3103c")
    val sykmeldingId = UUID.fromString("f9e3db67-2913-40c7-bb68-9a701d9fd4f4")

    afterEachTest {
        testDb.connection.dropData()
    }

    afterGroup {
        testDb.stop()
    }

    describe("Test av lagring av vedtaksinfo og planlagte meldinger") {
        val startdato = LocalDate.of(2020, 6, 1)
        val tom = LocalDate.of(2020, 7, 15)
        it("Lagrer vedtak, stansmelding og planlagt melding 4, 8, 39 uker for nytt tilfelle") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldEqual 4
            utbetaltEventFraDbListe.size shouldEqual 1
            val planlagtMelding4uker = planlagtMeldingFraDbListe.find { it.type == BREV_4_UKER_TYPE }
            val planlagtMelding8uker = planlagtMeldingFraDbListe.find { it.type == AKTIVITETSKRAV_8_UKER_TYPE }
            val planlagtMelding39uker = planlagtMeldingFraDbListe.find { it.type == BREV_39_UKER_TYPE }
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            val utbetaltEventFraDb = utbetaltEventFraDbListe.first()

            planlagtMelding4uker?.fnr shouldEqual "fnr"
            planlagtMelding4uker?.startdato shouldEqual startdato
            planlagtMelding4uker?.type shouldEqual BREV_4_UKER_TYPE
            planlagtMelding4uker?.sendes shouldEqual startdato.plusWeeks(4).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding4uker?.sendt shouldEqual null
            planlagtMelding4uker?.avbrutt shouldEqual null

            planlagtMelding8uker?.fnr shouldEqual "fnr"
            planlagtMelding8uker?.startdato shouldEqual startdato
            planlagtMelding8uker?.type shouldEqual AKTIVITETSKRAV_8_UKER_TYPE
            planlagtMelding8uker?.sendes shouldEqual startdato.plusWeeks(8).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding8uker?.sendt shouldEqual null
            planlagtMelding8uker?.avbrutt shouldEqual null

            planlagtMelding39uker?.fnr shouldEqual "fnr"
            planlagtMelding39uker?.startdato shouldEqual startdato
            planlagtMelding39uker?.type shouldEqual BREV_39_UKER_TYPE
            planlagtMelding39uker?.sendes shouldEqual startdato.plusWeeks(39).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMelding39uker?.sendt shouldEqual null
            planlagtMelding39uker?.avbrutt shouldEqual null

            planlagtStansmelding?.fnr shouldEqual "fnr"
            planlagtStansmelding?.startdato shouldEqual startdato
            planlagtStansmelding?.type shouldEqual STANS_TYPE
            planlagtStansmelding?.sendes shouldEqual tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtStansmelding?.sendt shouldEqual null
            planlagtStansmelding?.avbrutt shouldEqual null

            utbetaltEventFraDb shouldEqual utbetaltEvent
        }
        it("Lagrer kun vedtak og oppdaterer stansmelding, hvis planlagte meldinger finnes for syketilfellet fra før") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            val nesteUtbetaltEvent = lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), startdato, "fnr", tom.plusWeeks(1))

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldEqual 4
            utbetaltEventFraDbListe.size shouldEqual 2
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldEqual tom.plusWeeks(1).plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
        }
        it("Oppretter stansmelding hvis det ikke finnes fra før") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr", tom)
            testDb.connection.lagrePlanlagtMelding(opprettPlanlagtMelding(id = UUID.randomUUID(), fnr = "fnr", startdato = startdato))

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldEqual 2
            val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
            planlagtStansmelding?.sendes shouldEqual tom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
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
            planlagtMeldingFraDbListe.size shouldEqual 4
            nestePlanlagtMeldingFraDbListe.size shouldEqual 4
            utbetaltEventFraDbListe.size shouldEqual 1
            nesteUtbetaltEventFraDbListe.size shouldEqual 1
        }
    }
})
