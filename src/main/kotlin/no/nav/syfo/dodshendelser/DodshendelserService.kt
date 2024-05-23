package no.nav.syfo.dodshendelser

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
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
            if (personhendelse.isEmpty()) {
                delay(1.seconds)
            }
            personhendelse.forEach {
                if (it.hendelseGjelderDodsfall()) {
                    log.info(
                        "Mottatt dødshendelse med id ${it.finnHendelseId()}, avbryter eventuelle planlagte meldinger for avdød bruker"
                    )
                    handleDodsfall(it.hentPersonidenter())
                }
            }
            yield()
        }
    }

    @WithSpan
    fun handleDodsfall(personidenter: List<String>) {
        val antallAvbrutteMeldinger =
            database.avbrytPlanlagteMeldingerVedDodsfall(
                personidenter,
                OffsetDateTime.now(ZoneOffset.UTC)
            )
        if (antallAvbrutteMeldinger > 0) {
            log.info("Avbrøt $antallAvbrutteMeldinger melding(er) pga dødsfall")
            AVBRUTT_MELDING_DODSFALL.inc()
        }
    }

    private fun GenericRecord.hendelseGjelderDodsfall() =
        erDodsfall() && (hentEndringstype() == "OPPRETTET" || hentEndringstype() == "KORRIGERT")

    private fun GenericRecord.finnHendelseId() = get("hendelseId").toString()

    private fun GenericRecord.hentOpplysningstype() = get("opplysningstype").toString()

    private fun GenericRecord.erDodsfall() = hentOpplysningstype() == "DOEDSFALL_V1"

    private fun GenericRecord.hentEndringstype() = get("endringstype").toString()

    private fun GenericRecord.hentPersonidenter() =
        (get("personidenter") as GenericData.Array<*>).map { it.toString() }
}
