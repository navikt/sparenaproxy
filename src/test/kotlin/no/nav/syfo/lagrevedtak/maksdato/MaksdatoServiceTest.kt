package no.nav.syfo.lagrevedtak.maksdato

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutil.lagUtbetaltEvent
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object MaksdatoServiceTest : Spek({
    val arenaMqProducer = mockk<ArenaMqProducer>(relaxed = true)
    val pdlPersonService = mockk<PdlPersonService>()
    val maksdatoService = MaksdatoService(arenaMqProducer, pdlPersonService)

    beforeEachTest {
        clearAllMocks()
        coEvery { pdlPersonService.isAlive(any(), any()) } returns true
    }

    describe("Test av oppretting av maksdatomelding") {
        it("Oppretter riktig maksdatomelding") {
            val now = OffsetDateTime.of(LocalDate.of(2020, 9, 10).atTime(15, 20), ZoneOffset.UTC)
            val utbetaltEvent = lagUtbetaltEvent(
                id = UUID.randomUUID(),
                fnr = "12345678910",
                startdato = LocalDate.of(2020, 5, 2),
                gjenstaendeSykedager = 50,
                tom = LocalDate.of(2020, 9, 9),
                maksdato = LocalDate.of(2020, 12, 1)
            )

            val maksdatoMelding = maksdatoService.tilMaksdatoMelding(utbetaltEvent, now)

            maksdatoMelding.k278M810.dato shouldBeEqualTo "10092020"
            maksdatoMelding.k278M810.klokke shouldBeEqualTo "152000"
            maksdatoMelding.k278M810.fnr shouldBeEqualTo "12345678910"
            maksdatoMelding.k278M830.startdato shouldBeEqualTo "02052020"
            maksdatoMelding.k278M830.maksdato shouldBeEqualTo "01122020"
            maksdatoMelding.k278M830.orgnummer shouldBeEqualTo "orgnummer"
        }
        it("Beregner maksdato hvis maksdato mangler") {
            val now = OffsetDateTime.of(LocalDate.of(2020, 9, 10).atTime(15, 20), ZoneOffset.UTC)
            val utbetaltEvent = lagUtbetaltEvent(
                id = UUID.randomUUID(),
                fnr = "12345678910",
                startdato = LocalDate.of(2020, 5, 2),
                gjenstaendeSykedager = 50,
                tom = LocalDate.of(2020, 9, 9),
                maksdato = null
            )

            val maksdatoMelding = maksdatoService.tilMaksdatoMelding(utbetaltEvent, now)

            maksdatoMelding.k278M810.dato shouldBeEqualTo "10092020"
            maksdatoMelding.k278M810.klokke shouldBeEqualTo "152000"
            maksdatoMelding.k278M810.fnr shouldBeEqualTo "12345678910"
            maksdatoMelding.k278M830.startdato shouldBeEqualTo "02052020"
            maksdatoMelding.k278M830.maksdato shouldBeEqualTo "18112020"
            maksdatoMelding.k278M830.orgnummer shouldBeEqualTo "orgnummer"
        }
    }

    describe("Test av finnMaksdato") {
        it("Finner maksdato == fredag for tom = mandag og 4 gjenstående dager") {
            val tom = LocalDate.of(2020, 10, 5)
            val gjenstaendeSykedager = 4
            val maksdato = maksdatoService.finnMaksdato(tom, gjenstaendeSykedager)

            maksdato shouldBeEqualTo LocalDate.of(2020, 10, 9)
        }
        it("Finner maksdato == mandag for tom = mandag og 5 gjenstående dager") {
            val tom = LocalDate.of(2020, 10, 5)
            val gjenstaendeSykedager = 5
            val maksdato = maksdatoService.finnMaksdato(tom, gjenstaendeSykedager)

            maksdato shouldBeEqualTo LocalDate.of(2020, 10, 12)
        }
        it("Finner maksdato == tirsdag for tom = mandag og 6 gjenstående dager") {
            val tom = LocalDate.of(2020, 10, 5)
            val gjenstaendeSykedager = 6
            val maksdato = maksdatoService.finnMaksdato(tom, gjenstaendeSykedager)

            maksdato shouldBeEqualTo LocalDate.of(2020, 10, 13)
        }
    }

    describe("Test av logikk for om det skal sendes maksdato") {
        val fnr = "15060188888"
        val id = UUID.randomUUID()
        it("Skal sende maksdatomelding hvis forbrukte sykedager er mer enn 20 dager") {
            val forbrukteSykedager = 21
            runBlocking {
                maksdatoService.skalSendeMaksdatomelding(fnr, forbrukteSykedager, id) shouldBeEqualTo true
            }
        }
        it("Skal ikke sende maksdatomelding hvis forbrukte sykedager er mindre enn 20 dager") {
            val forbrukteSykedager = 19
            runBlocking {
                maksdatoService.skalSendeMaksdatomelding(fnr, forbrukteSykedager, id) shouldBeEqualTo false
            }
        }
        it("Skal ikke sende maksdatomelding hvis bruker er død") {
            coEvery { pdlPersonService.isAlive(fnr, id) } returns false
            val forbrukteSykedager = 21

            runBlocking {
                maksdatoService.skalSendeMaksdatomelding(fnr, forbrukteSykedager, id) shouldBeEqualTo false
            }
        }
    }
})
