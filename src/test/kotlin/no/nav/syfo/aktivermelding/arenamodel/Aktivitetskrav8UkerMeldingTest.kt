package no.nav.syfo.aktivermelding.arenamodel

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object Aktivitetskrav8UkerMeldingTest : Spek({
    val n2810 = N2810(
        dato = "19062020",
        klokke = "235157",
        fnr = "12345678910"
    )
    val n2830 = N2830(
        meldingsdata = "02052020                                                                                  " // lengde 90
    )
    val aktivitetskrav8UkerMelding = Aktivitetskrav8UkerMelding(
        n2810 = n2810,
        n2820 = N2820(),
        n2830 = n2830,
        n2840 = N2840()
    )

    describe("Aktivitetskravmelding får riktig tekstverdi") {
        it("N2810 får riktig tekstverdi") {
            val n2810SomTekst = n2810.tilTekst()

            n2810SomTekst shouldEqual "N2810   SENDMELDINGIT00         0048219062020235157    12345678910  SPO "
        }
        it("N2820 får riktig tekstverdi") {
            val n2820SomTekst = N2820().tilTekst()

            n2820SomTekst shouldEqual "N2820   00001                                                                                                                                                                                            " // lengde 201
        }
        it("N2830 får riktig tekstverdi") {
            val n2830SomTekst = n2830.tilTekst()

            n2830SomTekst shouldEqual "N2830   00001M-RK68-1  00802052020                                                                                  " // lengde 116
        }
        it("N2840 får riktig tekstverdi") {
            val n2840SomTekst = N2840().tilTekst()

            n2840SomTekst shouldEqual "N2840   00001INF: Aktivitetskrav ved 8 uker 100% sykmeldt                                    " // lengde 93
        }
        it("Aktivitetskravmelding får riktig tekstverdi") {
            val aktivitetskravMqMelding = aktivitetskrav8UkerMelding.tilMqMelding()

            aktivitetskravMqMelding shouldEqual "N2810   SENDMELDINGIT00         0048219062020235157    12345678910  SPO " +
                    "N2820   00001                                                                                                                                                                                            " +
                    "N2830   00001M-RK68-1  00802052020                                                                                  " +
                    "N2840   00001INF: Aktivitetskrav ved 8 uker 100% sykmeldt                                    " // lengde 482
        }
    }
})
