package no.nav.syfo.aktivermelding.arenamodel

import io.kotest.core.spec.style.FunSpec
import org.amshove.kluent.shouldBeEqualTo

class ArenameldingerTest :
    FunSpec({
        val n28108uker =
            N2810(dato = "19062020", klokke = "235157", fnr = "12345678910", meldKode = "O")
        val n28308uker =
            N2830(
                meldingId = "M-RK68-1  ",
                versjon = "008",
                meldingsdata =
                    "02052020                                                                                  " // lengde 90
            )
        val n28408uker =
            N2840(
                taglinje =
                    "SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 80
            )

        context("Aktivitetskravmelding får riktig tekstverdi") {
            test("N2810 får riktig tekstverdi") {
                val n2810SomTekst = n28108uker.tilTekst()

                n2810SomTekst shouldBeEqualTo
                    "N2810   SENDMELDINGSPEIL        0048219062020235157    12345678910  SPO "
            }
            test("N2820 får riktig tekstverdi") {
                val n2820SomTekst = N2820().tilTekst()

                n2820SomTekst shouldBeEqualTo
                    "N2820   00001                                                                                                                                                                                            " // lengde 201
            }
            test("N2830 får riktig tekstverdi") {
                val n2830SomTekst = n28308uker.tilTekst()

                n2830SomTekst shouldBeEqualTo
                    "N2830   00001M-RK68-1  00802052020                                                                                  " // lengde 116
            }
            test("N2840 får riktig tekstverdi") {
                val n2840SomTekst = n28408uker.tilTekst()

                n2840SomTekst shouldBeEqualTo
                    "N2840   00001SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 93
            }
        }
    })
