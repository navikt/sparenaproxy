package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import no.nav.syfo.Filter
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.db.avbrytPlanlagtMelding
import no.nav.syfo.aktivermelding.db.finnesPlanlagtMeldingMedNyereStartdato
import no.nav.syfo.aktivermelding.db.hentPlanlagtMelding
import no.nav.syfo.aktivermelding.db.sendPlanlagtMelding
import no.nav.syfo.aktivermelding.db.utsettPlanlagtMelding
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.AVBRUTT_MELDING
import no.nav.syfo.application.metrics.IKKE_FUNNET_MELDING
import no.nav.syfo.application.metrics.MOTTATT_AKTIVERMELDING
import no.nav.syfo.application.metrics.SENDT_MELDING
import no.nav.syfo.application.metrics.UTSATT_MELDING
import no.nav.syfo.db.fireukersmeldingErSendt
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.objectMapper
import no.nav.syfo.trefferAldersfilter

@KtorExperimentalAPI
class AktiverMeldingService(
    private val database: DatabaseInterface,
    private val smregisterClient: SmregisterClient,
    private val arenaMeldingService: ArenaMeldingService
) {
    suspend fun mottaAktiverMelding(record: String) {
        val aktiverMelding: AktiverMelding = objectMapper.readValue(record)
        log.info("Behandler melding med id {}", aktiverMelding.id)
        MOTTATT_AKTIVERMELDING.inc()
        behandleAktiverMelding(aktiverMelding)
    }

    suspend fun behandleAktiverMelding(aktiverMelding: AktiverMelding) {
        val planlagtMelding = database.hentPlanlagtMelding(aktiverMelding.id)
        if (planlagtMelding != null) {
            val finnesPlanlagtMeldingMedNyereStartdato =
                database.finnesPlanlagtMeldingMedNyereStartdato(planlagtMelding.fnr, planlagtMelding.startdato)
            if (finnesPlanlagtMeldingMedNyereStartdato) {
                log.info("Det finnes planlagte meldinger for sykefravær med nyere startdato, avbryter melding med id ${planlagtMelding.id}")
                avbrytMelding(aktiverMelding)
            } else if (planlagtMelding.type == STANS_TYPE) {
                sendEllerUtsettStansmelding(planlagtMelding)
            } else {
                val skalSendeMelding = when (planlagtMelding.type) {
                    BREV_4_UKER_TYPE -> {
                        smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                    }
                    AKTIVITETSKRAV_8_UKER_TYPE -> {
                        smregisterClient.er100ProsentSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                    }
                    BREV_39_UKER_TYPE -> {
                        smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                    }
                    else -> {
                        log.error("Planlagt melding med id ${planlagtMelding.id} har ukjent type: ${planlagtMelding.type}")
                        throw IllegalStateException("Planlagt melding har ukjent type")
                    }
                }
                if (skalSendeMelding) {
                    sendTilArena(planlagtMelding)
                } else {
                    avbrytMelding(aktiverMelding)
                }
            }
        } else {
            log.warn("Fant ikke planlagt melding for id ${aktiverMelding.id} som ikke er sendt eller avbrutt fra før")
            IKKE_FUNNET_MELDING.inc()
        }
    }

    private suspend fun sendEllerUtsettStansmelding(planlagtMelding: PlanlagtMeldingDbModel) {
        val sykmeldtTom = smregisterClient.erSykmeldtTilOgMed(planlagtMelding.fnr, planlagtMelding.id)
        if (sykmeldtTom == null) {
            if (trefferAldersfilter(planlagtMelding.fnr, Filter.ETTER1995) && database.fireukersmeldingErSendt(planlagtMelding.fnr, planlagtMelding.startdato)) {
                log.info("Bruker er ikke lenger sykmeldt, aktiverer stansmelding ${planlagtMelding.id}")
                sendTilArena(planlagtMelding)
            } else {
                log.info("Bruker er ikke lenger sykmeldt, men var sykmeldt mindre enn 4 uker/treffer ikke filter, stansmelding ${planlagtMelding.id}")
                avbrytMelding(AktiverMelding(planlagtMelding.id))
            }
        } else {
            log.info("Bruker er fortsatt sykmeldt, utsetter stansmelding ${planlagtMelding.id}")
            database.utsettPlanlagtMelding(planlagtMelding.id, sykmeldtTom.plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime())
            UTSATT_MELDING.inc()
        }
    }

    private fun sendTilArena(planlagtMelding: PlanlagtMeldingDbModel) {
        log.info("Sender melding med id {} til Arena", planlagtMelding.id)
        arenaMeldingService.sendPlanlagtMeldingTilArena(planlagtMelding)
        database.sendPlanlagtMelding(planlagtMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
        SENDT_MELDING.inc()
    }

    private fun avbrytMelding(aktiverMelding: AktiverMelding) {
        log.info("Avbryter melding med id {}", aktiverMelding.id)
        database.avbrytPlanlagtMelding(aktiverMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
        AVBRUTT_MELDING.inc()
    }
}
