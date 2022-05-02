package no.nav.syfo.lagrevedtak.maksdato

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class MaksdatoMeldingTest : FunSpec({
    val k278M810 = K278M810(
        dato = "19062020",
        klokke = "235157",
        fnr = "12345678910"
    )
    val k278M830 = K278M830(
        startdato = "28052020",
        maksdato = "27052021",
        orgnummer = "998899889"
    )

    val maksdatoMelding = MaksdatoMelding(
        k278M810 = k278M810,
        k278M815 = K278M815(),
        k278M830 = k278M830,
        k278M840 = K278M840()
    )

    context("Maksdatomelding får riktig tekstverdi") {
        test("K278M810 får riktig tekstverdi") {
            val k278M810SomTekst = k278M810.tilTekst()

            k278M810SomTekst shouldBeEqualTo "K278M810SENDMELDINGSPEIL        0048219062020235157    12345678910  SPI "
        }
        test("K278M815 får riktig tekstverdi") {
            val k278M815SomTekst = K278M815().tilTekst()

            k278M815SomTekst shouldBeEqualTo "K278M81500001                                                                                                                                                                                            " // lengde 201
        }
        test("K278M830 får riktig tekstverdi") {
            val k278M830SomTekst = k278M830.tilTekst()

            k278M830SomTekst shouldBeEqualTo "K278M83000001M-SPVT-1  0052805202027052021998899889                                                                 " // lengde 116
        }
        test("K278M840 får riktig tekstverdi") {
            val k278M840SomTekst = K278M840().tilTekst()

            k278M840SomTekst shouldBeEqualTo "K278M84000001SP: SP, max.dato sykepenger                                                     " // lengde 93
        }
        test("Maksdatomelding får riktig tekstverdi") {
            val maksdatoMqMelding = maksdatoMelding.tilMqMelding()

            maksdatoMqMelding shouldBeEqualTo "K278M810SENDMELDINGSPEIL        0048219062020235157    12345678910  SPI " +
                "K278M81500001                                                                                                                                                                                            " +
                "K278M83000001M-SPVT-1  0052805202027052021998899889                                                                 " +
                "K278M84000001SP: SP, max.dato sykepenger                                                     " // lengde 482
        }
    }
})
