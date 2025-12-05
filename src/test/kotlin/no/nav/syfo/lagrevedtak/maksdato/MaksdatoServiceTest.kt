package no.nav.syfo.lagrevedtak.maksdato

import io.kotest.core.spec.style.FunSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.pdl.service.PdlPersonService
import no.nav.syfo.testutil.lagUtbetaltEvent
import org.amshove.kluent.shouldBeEqualTo

class MaksdatoServiceTest :
    FunSpec({
        val arenaMqProducer = mockk<ArenaMqProducer>(relaxed = true)
        val pdlPersonService = mockk<PdlPersonService>()
        val maksdatoService = MaksdatoService(arenaMqProducer, pdlPersonService)

        beforeTest {
            clearAllMocks()
            coEvery { pdlPersonService.isAlive(any(), any()) } returns true
        }

        context("Test av oppretting av maksdatomelding") {
            test("Oppretter riktig maksdatomelding") {
                val now =
                    OffsetDateTime.of(LocalDate.of(2020, 9, 10).atTime(15, 20), ZoneOffset.UTC)
                val utbetaltEvent =
                    lagUtbetaltEvent(
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
        }

        context("Test av logikk for om det skal sendes maksdato") {
            val fnr = "15060188888"
            val id = UUID.randomUUID()
            test("Skal sende maksdatomelding hvis forbrukte sykedager er mer enn 20 dager") {
                val forbrukteSykedager = 21

                maksdatoService.skalSendeMaksdatomelding(
                    fnr,
                    forbrukteSykedager,
                    id
                ) shouldBeEqualTo true
            }
            test(
                "Skal ikke sende maksdatomelding hvis forbrukte sykedager er mindre enn 20 dager"
            ) {
                val forbrukteSykedager = 19

                maksdatoService.skalSendeMaksdatomelding(
                    fnr,
                    forbrukteSykedager,
                    id
                ) shouldBeEqualTo false
            }
            test("Skal ikke sende maksdatomelding hvis bruker er død") {
                coEvery { pdlPersonService.isAlive(fnr, id) } returns false
                val forbrukteSykedager = 21

                maksdatoService.skalSendeMaksdatomelding(
                    fnr,
                    forbrukteSykedager,
                    id
                ) shouldBeEqualTo false
            }

            test("Maksdatomelding med SELVSTENDIG") {
                val utbetalingsevent =
                    lagUtbetaltEvent(
                        id = UUID.randomUUID(),
                        fnr = "12345678910",
                        startdato = LocalDate.of(2020, 5, 2),
                        gjenstaendeSykedager = 50,
                        tom = LocalDate.of(2020, 9, 9),
                        maksdato = LocalDate.of(2020, 12, 1),
                        orgnummer = "SELVSTENDIG",
                    )
                val maksdatomelding =
                    maksdatoService.tilMaksdatoMelding(utbetalingsevent, now = OffsetDateTime.now())
                maksdatomelding.tilMqMelding()
            }
        }
    })
