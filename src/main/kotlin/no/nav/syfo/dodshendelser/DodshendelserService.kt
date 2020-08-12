package no.nav.syfo.dodshendelser

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.delay
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.AVBRUTT_MELDING_DODSFALL
import no.nav.syfo.dodshendelser.db.avbrytPlanlagteMeldingerVedDodsfall
import no.nav.syfo.dodshendelser.kafka.PersonhendelserConsumer
import no.nav.syfo.log
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

class DodshendelserService(
    private val applicationState: ApplicationState,
    private val personhendelserConsumer: PersonhendelserConsumer,
    private val database: DatabaseInterface
) {
    suspend fun start() {
        while (applicationState.ready) {
            val personhendelse = personhendelserConsumer.poll()
            personhendelse.forEach {
                log.info("Debug: Leser melding")
                if (it.hendelseGjelderDodsfall()) {
                    handleDodsfall(it.hentPersonidenter())
                }
            }
            delay(1)
        }
    }

    fun handleDodsfall(personidenter: List<String>) {
        log.info("Avbryter eventuelle planlagte meldinger for avdød bruker")
        database.avbrytPlanlagteMeldingerVedDodsfall(personidenter, OffsetDateTime.now(ZoneOffset.UTC))
        AVBRUTT_MELDING_DODSFALL.inc()
    }

    fun GenericRecord.hendelseGjelderDodsfall() =
        erDodsfall() && (hentEndringstype() == "OPPRETTET" || hentEndringstype() == "KORRIGERT")

    private fun GenericRecord.hentOpplysningstype() =
        get("opplysningstype").toString()

    private fun GenericRecord.erDodsfall() =
        hentOpplysningstype() == "DOEDSFALL_V1"

    private fun GenericRecord.hentEndringstype() =
        get("endringstype").toString()

    private fun GenericRecord.hentPersonidenter() =
        (get("personidenter") as GenericData.Array<*>)
            .map { it.toString() }
}
