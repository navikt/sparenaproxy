package no.nav.syfo.aktivermelding.arenamodel

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class StansmeldingTest :
    FunSpec({
        val k278M810 = K278M810Stans(dato = "19062020", klokke = "235157", fnr = "12345678910")
        val k278M830 = K278M830Stans(startdato = "28052020")

        val maksdatoMelding =
            Stansmelding(
                k278M810 = k278M810,
                k278M815 = K278M815Stans(),
                k278M830 = k278M830,
                k278M840 = K278M840Stans()
            )

        context("Stansmelding får riktig tekstverdi") {
            test("K278M810 får riktig tekstverdi") {
                val k278M810SomTekst = k278M810.tilTekst()

                k278M810SomTekst shouldBeEqualTo
                    "K278M810SENDMELDINGSPEIL        0048219062020235157    12345678910  SPO "
            }
            test("K278M815 får riktig tekstverdi") {
                val k278M815SomTekst = K278M815Stans().tilTekst()

                k278M815SomTekst shouldBeEqualTo
                    "K278M81500001                                                                                                                                                                                            " // lengde 201
            }
            test("K278M830 får riktig tekstverdi") {
                val k278M830SomTekst = k278M830.tilTekst()

                k278M830SomTekst shouldBeEqualTo
                    "K278M83000001M-SPUB-1  00528052020AAAVSLUTTET                                                                       " // lengde 116
            }
            test("K278M840 får riktig tekstverdi") {
                val k278M840SomTekst = K278M840Stans().tilTekst()

                k278M840SomTekst shouldBeEqualTo
                    "K278M84000001SP: SP sykepenger er stanset                                                    " // lengde 93
            }
            test("Maksdatomelding får riktig tekstverdi") {
                val maksdatoMqMelding = maksdatoMelding.tilMqMelding()

                maksdatoMqMelding shouldBeEqualTo
                    "K278M810SENDMELDINGSPEIL        0048219062020235157    12345678910  SPO " +
                        "K278M81500001                                                                                                                                                                                            " +
                        "K278M83000001M-SPUB-1  00528052020AAAVSLUTTET                                                                       " +
                        "K278M84000001SP: SP sykepenger er stanset                                                    " // lengde 482
            }
        }
    })
