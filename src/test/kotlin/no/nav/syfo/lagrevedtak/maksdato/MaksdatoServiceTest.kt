package no.nav.syfo.lagrevedtak.maksdato

import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.testutil.lagUtbetaltEvent
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object MaksdatoServiceTest : Spek({
    val arenaMqProducer = mockk<ArenaMqProducer>(relaxed = true)
    val maksdatoService = MaksdatoService(arenaMqProducer)

    describe("Test av oppretting av maksdatomelding") {
        it("Oppretter riktig maksdatomelding") {
            val now = OffsetDateTime.of(LocalDate.of(2020, 9, 10).atTime(15, 20), ZoneOffset.UTC)
            val utbetaltEvent = lagUtbetaltEvent(
                id = UUID.randomUUID(),
                fnr = "12345678910",
                startdato = LocalDate.of(2020, 5, 2),
                sykmeldingId = UUID.randomUUID(),
                gjenstaendeSykedager = 50,
                opprettet = LocalDateTime.of(2020, 9, 9, 16, 0, 0)
            )

            val maksdatoMelding = maksdatoService.tilMaksdatoMelding(utbetaltEvent, now)

            maksdatoMelding.k278M810.dato shouldEqual "10092020"
            maksdatoMelding.k278M810.klokke shouldEqual "152000"
            maksdatoMelding.k278M810.fnr shouldEqual "12345678910"
            maksdatoMelding.k278M830.startdato shouldEqual "02052020"
            maksdatoMelding.k278M830.maksdato shouldEqual "29102020"
            maksdatoMelding.k278M830.orgnummer shouldEqual "orgnummer"
        }
    }

    describe("Test av finnMaksdato") {
        it("Finner riktig maksdato") {
            val utbetaltEvent = lagUtbetaltEvent(
                id = UUID.randomUUID(),
                fnr = "12345678910",
                startdato = LocalDate.of(2020, 5, 2),
                sykmeldingId = UUID.randomUUID(),
                gjenstaendeSykedager = 3,
                opprettet = LocalDateTime.of(2020, 10, 10, 16, 0, 0)
            )

            val maksdato = maksdatoService.finnMaksdato(utbetaltEvent)

            maksdato shouldEqual LocalDate.of(2020, 10, 13)
        }
    }
})
