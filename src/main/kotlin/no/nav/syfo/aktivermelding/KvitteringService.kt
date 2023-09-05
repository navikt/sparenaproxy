package no.nav.syfo.aktivermelding

import no.nav.syfo.aktivermelding.arenamodel.tilKvittering
import no.nav.syfo.aktivermelding.db.resendPlanlagtMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.KVITTERING_MED_FEIL
import no.nav.syfo.application.metrics.KVITTERING_SENDT
import no.nav.syfo.log
import no.nav.syfo.securelog

class KvitteringService(private val database: DatabaseInterface) {
    fun behandleKvittering(kvitteringsmelding: String, correlationId: String) {
        val kvittering = tilKvittering(kvitteringsmelding)

        if (kvittering.statusOk == "J") {
            log.info("Mottatt ok-kvittering fra Arena")
            KVITTERING_SENDT.inc()
        } else {
            KVITTERING_MED_FEIL.inc()
            securelog.info("Kvittering: $kvittering melding med id $correlationId")
            log.error(
                "Melding med id $correlationId har feilet i Arena, statusOk: ${kvittering.statusOk}, feilkode: ${kvittering.feilkode}, feilmelding ${kvittering.feilmelding}"
            )
            val antallResendteMeldinger = database.resendPlanlagtMelding(correlationId)
            if (antallResendteMeldinger < 1) {
                log.error("Fant ikke melding med id $correlationId, kan ikke resende")
                throw RuntimeException(
                    "Melding med id $correlationId har feilet i Arena: ${kvittering.feilkode} og kan ikke resendes"
                )
            } else {
                log.info("Resender melding med id $correlationId")
            }
        }
    }
}
