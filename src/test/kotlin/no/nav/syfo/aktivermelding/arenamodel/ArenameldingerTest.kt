package no.nav.syfo.aktivermelding.arenamodel

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ArenameldingerTest : Spek({
    val n28108uker = N2810(
        dato = "19062020",
        klokke = "235157",
        fnr = "12345678910",
        meldKode = "O"
    )
    val n28308uker = N2830(
        meldingId = "M-RK68-1  ",
        versjon = "008",
        meldingsdata = "02052020                                                                                  " // lengde 90
    )
    val n28408uker = N2840(
        taglinje = "SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 80
    )
    val aktivitetskrav8UkerMelding = Aktivitetskrav8UkerMelding(
        n2810 = n28108uker,
        n2820 = N2820(),
        n2830 = n28308uker,
        n2840 = n28408uker
    )

    val n281039uker = N2810(
        dato = "19062020",
        klokke = "235157",
        fnr = "12345678910",
        meldKode = "I"
    )
    val n283039uker = N2830(
        meldingId = "M-F226-1  ",
        versjon = "015",
        meldingsdata = "02052020                                                                                  " // lengde 90
    )
    val n284039uker = N2840(
        taglinje = "SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).    " // lengde 80
    )
    val brev39UkerMelding = Brev39UkerMelding(
        n2810 = n281039uker,
        n2820 = N2820(),
        n2830 = n283039uker,
        n2840 = n284039uker
    )

    describe("Aktivitetskravmelding får riktig tekstverdi") {
        it("N2810 får riktig tekstverdi") {
            val n2810SomTekst = n28108uker.tilTekst()

            n2810SomTekst shouldBeEqualTo "N2810   SENDMELDINGSPEIL        0048219062020235157    12345678910  SPO "
        }
        it("N2820 får riktig tekstverdi") {
            val n2820SomTekst = N2820().tilTekst()

            n2820SomTekst shouldBeEqualTo "N2820   00001                                                                                                                                                                                            " // lengde 201
        }
        it("N2830 får riktig tekstverdi") {
            val n2830SomTekst = n28308uker.tilTekst()

            n2830SomTekst shouldBeEqualTo "N2830   00001M-RK68-1  00802052020                                                                                  " // lengde 116
        }
        it("N2840 får riktig tekstverdi") {
            val n2840SomTekst = n28408uker.tilTekst()

            n2840SomTekst shouldBeEqualTo "N2840   00001SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 93
        }
        it("Aktivitetskravmelding får riktig tekstverdi") {
            val aktivitetskravMqMelding = aktivitetskrav8UkerMelding.tilMqMelding()

            aktivitetskravMqMelding shouldBeEqualTo "N2810   SENDMELDINGSPEIL        0048219062020235157    12345678910  SPO " +
                "N2820   00001                                                                                                                                                                                            " +
                "N2830   00001M-RK68-1  00802052020                                                                                  " +
                "N2840   00001SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 482
        }
    }

    describe("39-ukersmelding får riktig tekstverdi") {
        it("N2810 får riktig tekstverdi") {
            val n2810SomTekst = n281039uker.tilTekst()

            n2810SomTekst shouldBeEqualTo "N2810   SENDMELDINGSPEIL        0048219062020235157    12345678910  SPI "
        }
        it("N2830 får riktig tekstverdi") {
            val n2830SomTekst = n283039uker.tilTekst()

            n2830SomTekst shouldBeEqualTo "N2830   00001M-F226-1  01502052020                                                                                  " // lengde 116
        }
        it("N2840 får riktig tekstverdi") {
            val n2840SomTekst = n284039uker.tilTekst()

            n2840SomTekst shouldBeEqualTo "N2840   00001SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).    " // lengde 93
        }
        it("39-ukersmelding får riktig tekstverdi") {
            val brev39UkerMqMelding = brev39UkerMelding.tilMqMelding()

            brev39UkerMqMelding shouldBeEqualTo "N2810   SENDMELDINGSPEIL        0048219062020235157    12345678910  SPI " +
                "N2820   00001                                                                                                                                                                                            " +
                "N2830   00001M-F226-1  01502052020                                                                                  " +
                "N2840   00001SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).    " // lengde 482
        }
    }
})
