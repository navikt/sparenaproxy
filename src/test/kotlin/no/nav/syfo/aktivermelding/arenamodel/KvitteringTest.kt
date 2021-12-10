package no.nav.syfo.aktivermelding.arenamodel

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KvitteringTest : Spek({
    val kvitteringsmelding = "K278M890Kvittering Arena002270307202010070612345678910J                                                                                                                                                                            "
    val kvitteringsmeldingMedFeil = "K278M890Kvittering Arena002270307202010070612345678910NXXXXXXXXFeilmelding                                                                                                                                                         "

    describe("Kvitteringstest") {
        it("tilKvittering mapper ok kvitteringstekst riktig") {
            val kvittering = tilKvittering(kvitteringsmelding)

            kvittering.copyId shouldBeEqualTo "K278M890"
            kvittering.aksjon shouldBeEqualTo "Kvittering "
            kvittering.kilde shouldBeEqualTo "Arena"
            kvittering.mlen shouldBeEqualTo "00227"
            kvittering.dato shouldBeEqualTo "03072020"
            kvittering.klokke shouldBeEqualTo "100706"
            kvittering.fnr shouldBeEqualTo "12345678910"
            kvittering.statusOk shouldBeEqualTo "J"
            kvittering.feilkode shouldBeEqualTo "        "
            kvittering.feilmelding shouldBeEqualTo "                                                                                                                                                                    "
        }
        it("tilKvittering mapper kvitteringstekst med feil riktig") {
            val kvittering = tilKvittering(kvitteringsmeldingMedFeil)

            kvittering.copyId shouldBeEqualTo "K278M890"
            kvittering.aksjon shouldBeEqualTo "Kvittering "
            kvittering.kilde shouldBeEqualTo "Arena"
            kvittering.mlen shouldBeEqualTo "00227"
            kvittering.dato shouldBeEqualTo "03072020"
            kvittering.klokke shouldBeEqualTo "100706"
            kvittering.fnr shouldBeEqualTo "12345678910"
            kvittering.statusOk shouldBeEqualTo "N"
            kvittering.feilkode shouldBeEqualTo "XXXXXXXX"
            kvittering.feilmelding shouldBeEqualTo "Feilmelding                                                                                                                                                         "
        }
    }
})
