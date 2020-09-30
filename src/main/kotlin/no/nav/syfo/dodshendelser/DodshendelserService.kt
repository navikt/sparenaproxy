package no.nav.syfo.dodshendelser

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.OffsetDateTime
import java.time.ZoneOffset
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.AVBRUTT_MELDING_DODSFALL
import no.nav.syfo.dodshendelser.db.avbrytPlanlagteMeldingerVedDodsfall
import no.nav.syfo.log
import no.nav.syfo.objectMapper
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord

class DodshendelserService(
    private val database: DatabaseInterface
) {
    fun handlePersonhendelse(record: String) {
        val genericRecord: GenericRecord = objectMapper.readValue(record)
        if (genericRecord.hendelseGjelderDodsfall()) {
            handleDodsfall(genericRecord.hentPersonidenter())
        }
    }

    fun handleDodsfall(personidenter: List<String>) {
        log.info("Avbryter eventuelle planlagte meldinger for avdød bruker")
        val antallAvbrutteMeldinger = database.avbrytPlanlagteMeldingerVedDodsfall(personidenter, OffsetDateTime.now(ZoneOffset.UTC))
        if (antallAvbrutteMeldinger > 0) {
            log.info("Avbrøt $antallAvbrutteMeldinger melding(er) pga dødsfall")
            AVBRUTT_MELDING_DODSFALL.inc()
        }
    }

    private fun GenericRecord.hendelseGjelderDodsfall() =
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
