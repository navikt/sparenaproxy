package no.nav.syfo.aktivermelding

import no.nav.syfo.aktivermelding.arenamodel.tilKvittering
import no.nav.syfo.application.metrics.KVITTERING_MED_FEIL
import no.nav.syfo.application.metrics.KVITTERING_SENDT
import no.nav.syfo.log

class KvitteringService() {
    fun behandleKvittering(kvitteringsmelding: String) {
        val kvittering = tilKvittering(kvitteringsmelding)

        if (kvittering.statusOk == "J") {
            log.info("Mottatt ok-kvittering fra Arena")
            KVITTERING_SENDT.inc()
        } else {
            KVITTERING_MED_FEIL.inc()
            log.error("Melding har feilet i Arena, feilkode: ${kvittering.feilkode}, feilmelding ${kvittering.feilmelding}")
            throw RuntimeException("Melding har feilet i Arena: ${kvittering.feilkode}")
        }
    }
}
