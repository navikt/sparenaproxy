package no.nav.syfo.aktivermelding

import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.aktivermelding.arenamodel.tilKvittering
import no.nav.syfo.aktivermelding.db.finnPlanlagtMeldingUnderSending
import no.nav.syfo.aktivermelding.db.sendPlanlagtMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.KVITTERING_SENDT
import no.nav.syfo.log

class KvitteringService(
    private val database: DatabaseInterface
) {
    fun behandleKvittering(kvitteringsmelding: String) {
        val kvittering = tilKvittering(kvitteringsmelding)

        if (kvittering.statusOk == "J") {
            val planlagtMeldingDbModel = database.finnPlanlagtMeldingUnderSending(kvittering.fnr)
            if (planlagtMeldingDbModel != null) {
                database.sendPlanlagtMelding(planlagtMeldingDbModel.id, OffsetDateTime.now(ZoneOffset.UTC))
                log.info("Planlagt melding med id ${planlagtMeldingDbModel.id} registrert sendt")
                KVITTERING_SENDT.inc()
            }
        } else {
            log.error("Melding har feilet i Arena, feilkode: ${kvittering.feilkode}, feilmelding ${kvittering.feilmelding}")
            throw RuntimeException("Melding har feilet i Arena: ${kvittering.feilkode}")
        }
    }
}
