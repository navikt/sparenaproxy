package no.nav.syfo.aktivermelding

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.time.delay
import no.nav.syfo.aktivermelding.arenamodel.tilKvittering
import no.nav.syfo.aktivermelding.db.resendPlanlagtMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.KVITTERING_MED_FEIL
import no.nav.syfo.application.metrics.KVITTERING_SENDT
import no.nav.syfo.log
import no.nav.syfo.securelog

class KvitteringService(
    private val database: DatabaseInterface,
    val dbUpdateRetires: Int = 5,
    val dbUpdateTimeout: Duration = 1.seconds
) {

    @WithSpan
    suspend fun behandleKvittering(kvitteringsmelding: String, correlationId: String) {
        val kvittering = tilKvittering(kvitteringsmelding)

        if (kvittering.statusOk == "J") {
            log.info("Mottatt ok-kvittering fra Arena")
            securelog.info("Kvittering: $kvittering melding med id $correlationId")
            KVITTERING_SENDT.inc()
        } else {
            KVITTERING_MED_FEIL.inc()
            securelog.info("Kvittering: $kvittering melding med id $correlationId")
            log.warn(
                "Melding med id $correlationId har feilet i Arena, statusOk: ${kvittering.statusOk}, feilkode: ${kvittering.feilkode}, feilmelding ${kvittering.feilmelding}"
            )
            val antallResendteMeldinger = tryUpdatePlanlagtMelding(correlationId)
            if (antallResendteMeldinger < 1) {
                log.info("Fant ikke melding med id $correlationId, kan ikke resende")
            } else {
                log.info("Resender melding med id $correlationId")
            }
        }
    }

    suspend fun tryUpdatePlanlagtMelding(correlationId: String): Int {
        repeat(dbUpdateRetires) {
            val r = database.resendPlanlagtMelding(correlationId)
            if (r > 0) return r else delay(dbUpdateTimeout)
        }
        return 0
    }
}
