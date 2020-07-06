package no.nav.syfo.aktivermelding.arenamodel

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KvitteringTest : Spek({
    val kvitteringsmelding = "K278M890Kvittering Arena002270307202010070612345678910J                                                                                                                                                                            "
    val kvitteringsmeldingMedFeil = "K278M890Kvittering Arena002270307202010070612345678910NXXXXXXXXFeilmelding                                                                                                                                                         "

    describe("Kvitteringstest") {
        it("tilKvittering mapper ok kvitteringstekst riktig") {
            val kvittering = tilKvittering(kvitteringsmelding)

            kvittering.copyId shouldEqual "K278M890"
            kvittering.aksjon shouldEqual "Kvittering "
            kvittering.kilde shouldEqual "Arena"
            kvittering.mlen shouldEqual "00227"
            kvittering.dato shouldEqual "03072020"
            kvittering.klokke shouldEqual "100706"
            kvittering.fnr shouldEqual "12345678910"
            kvittering.statusOk shouldEqual "J"
            kvittering.feilkode shouldEqual "        "
            kvittering.feilmelding shouldEqual "                                                                                                                                                                    "
        }
        it("tilKvittering mapper kvitteringstekst med feil riktig") {
            val kvittering = tilKvittering(kvitteringsmeldingMedFeil)

            kvittering.copyId shouldEqual "K278M890"
            kvittering.aksjon shouldEqual "Kvittering "
            kvittering.kilde shouldEqual "Arena"
            kvittering.mlen shouldEqual "00227"
            kvittering.dato shouldEqual "03072020"
            kvittering.klokke shouldEqual "100706"
            kvittering.fnr shouldEqual "12345678910"
            kvittering.statusOk shouldEqual "N"
            kvittering.feilkode shouldEqual "XXXXXXXX"
            kvittering.feilmelding shouldEqual "Feilmelding                                                                                                                                                         "
        }
    }
})
