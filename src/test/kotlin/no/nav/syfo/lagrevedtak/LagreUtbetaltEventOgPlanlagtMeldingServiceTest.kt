package no.nav.syfo.lagrevedtak

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.hentUtbetaltEvent
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
        it("Lagrer vedtak og planlagt melding 8 uker for nytt tilfelle") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr")

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldEqual 1
            utbetaltEventFraDbListe.size shouldEqual 1
            val planlagtMeldingFraDb = planlagtMeldingFraDbListe.first()
            val utbetaltEventFraDb = utbetaltEventFraDbListe.first()

            planlagtMeldingFraDb.fnr shouldEqual "fnr"
            planlagtMeldingFraDb.startdato shouldEqual startdato
            planlagtMeldingFraDb.type shouldEqual AKTIVITETSKRAV_8_UKER_TYPE
            planlagtMeldingFraDb.sendes shouldEqual startdato.plusWeeks(8).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            planlagtMeldingFraDb.sendt shouldEqual null
            planlagtMeldingFraDb.avbrutt shouldEqual null

            utbetaltEventFraDb shouldEqual utbetaltEvent
        }
        it("Lagrer kun vedtak, ikke melding, hvis planlagt melding finnes for syketilfellet fra før") {
            val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, sykmeldingId, startdato, "fnr")
            val nesteUtbetaltEvent = lagUtbetaltEvent(UUID.randomUUID(), UUID.randomUUID(), startdato, "fnr")

            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(utbetaltEvent)
            lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(nesteUtbetaltEvent)

            val planlagtMeldingFraDbListe = testDb.connection.hentPlanlagtMelding("fnr", startdato)
            val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
            planlagtMeldingFraDbListe.size shouldEqual 1
            utbetaltEventFraDbListe.size shouldEqual 2
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
            planlagtMeldingFraDbListe.size shouldEqual 1
            nestePlanlagtMeldingFraDbListe.size shouldEqual 1
            utbetaltEventFraDbListe.size shouldEqual 1
            nesteUtbetaltEventFraDbListe.size shouldEqual 1
        }
    }
})

fun lagUtbetaltEvent(id: UUID, sykmeldingId: UUID, startdato: LocalDate, fnr: String): UtbetaltEvent =
    UtbetaltEvent(
        utbetalteventid = id,
        startdato = startdato,
        sykmeldingid = sykmeldingId,
        aktorid = "aktorid",
        fnr = fnr,
        organisasjonsnummer = "orgnummer",
        hendelser = listOf(UUID.randomUUID(), UUID.randomUUID()).toSet(),
        oppdrag = lagOppdragsliste(),
        fom = startdato,
        tom = LocalDate.of(2020, 6, 29),
        forbrukteSykedager = 0,
        gjenstaendeSykedager = 300,
        opprettet = LocalDateTime.now()
    )

fun lagOppdragsliste(): List<Utbetalt> {
    return listOf(
        Utbetalt(
            mottaker = "mottaker",
            fagomrade = "sykepenger",
            fagsystemId = "id",
            totalbelop = 6000,
            utbetalingslinjer = lagUbetalingslinjeliste()
        )
    )
}

fun lagUbetalingslinjeliste(): List<Utbetalingslinje> {
    return listOf(
        Utbetalingslinje(
            fom = LocalDate.of(2020, 6, 1),
            tom = LocalDate.of(2020, 6, 29),
            dagsats = 500,
            belop = 2000,
            grad = 70.0,
            sykedager = 20
        )
    )
}
