package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.db.avbrytPlanlagtMelding
import no.nav.syfo.aktivermelding.db.erStansmeldingSendt
import no.nav.syfo.aktivermelding.db.finnesNyerePlanlagtMeldingMedAnnenStartdato
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
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.db.fireukersmeldingErSendt
import no.nav.syfo.dodshendelser.db.avbrytPlanlagteMeldingerVedDodsfall
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.objectMapper
import no.nav.syfo.pdl.service.PdlPersonService
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class AktiverMeldingService(
    private val database: DatabaseInterface,
    private val smregisterClient: SmregisterClient,
    private val arenaMeldingService: ArenaMeldingService,
    private val pdlPersonService: PdlPersonService,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient
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
            val finnesNyerePlanlagtMeldingMedAnnenStartdato =
                database.finnesNyerePlanlagtMeldingMedAnnenStartdato(planlagtMelding.fnr, planlagtMelding.startdato, planlagtMelding.opprettet)
            if (finnesNyerePlanlagtMeldingMedAnnenStartdato) {
                log.info("Det finnes nyere planlagte meldinger for sykefravær med annen startdato, avbryter melding med id ${planlagtMelding.id}")
                avbrytMelding(aktiverMelding)
            } else if (planlagtMelding.type == STANS_TYPE) {
                sendEllerUtsettStansmelding(planlagtMelding)
            } else {
                val skalSendeMelding = when (planlagtMelding.type) {
                    BREV_4_UKER_TYPE -> {
                        smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)
                    }
                    AKTIVITETSKRAV_8_UKER_TYPE -> {
                        if (smregisterClient.er100ProsentSykmeldt(planlagtMelding.fnr, aktiverMelding.id)) {
                            gjelderSammeSykefravaer(planlagtMelding)
                        } else {
                            false
                        }
                    }
                    BREV_39_UKER_TYPE -> {
                        if (smregisterClient.erSykmeldt(planlagtMelding.fnr, aktiverMelding.id)) {
                            gjelderSammeSykefravaer(planlagtMelding)
                        } else {
                            false
                        }
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
            if (database.fireukersmeldingErSendt(planlagtMelding.fnr, planlagtMelding.startdato)) {
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

    private suspend fun sendTilArena(planlagtMelding: PlanlagtMeldingDbModel) {
        if (pdlPersonService.isAlive(planlagtMelding.fnr, planlagtMelding.id)) {
            log.info("Sender melding med id {} til Arena", planlagtMelding.id)
            val correlationId = arenaMeldingService.sendPlanlagtMeldingTilArena(planlagtMelding)
            database.sendPlanlagtMelding(planlagtMelding.id, OffsetDateTime.now(ZoneOffset.UTC), correlationId)
            SENDT_MELDING.inc()
        } else {
            log.info("Person er død, avbryter alle planlagte meldinger ${planlagtMelding.id}")
            avbrytPgaDodsfall(planlagtMelding.fnr, planlagtMelding.id)
        }
    }

    private suspend fun gjelderSammeSykefravaer(planlagtMelding: PlanlagtMeldingDbModel): Boolean {
        if (database.erStansmeldingSendt(planlagtMelding.fnr, planlagtMelding.startdato)) {
            log.info("Det er sendt/avbrutt stansmelding for sykefravær som melding med id ${planlagtMelding.id} tilhører")
            return !syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(planlagtMelding.fnr, planlagtMelding.startdato, planlagtMelding.id)
        }
        return true
    }

    private fun avbrytMelding(aktiverMelding: AktiverMelding) {
        log.info("Avbryter melding med id {}", aktiverMelding.id)
        database.avbrytPlanlagtMelding(aktiverMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
        AVBRUTT_MELDING.inc()
    }

    private fun avbrytPgaDodsfall(fnr: String, meldingId: UUID) {
        val antallAvbrutteMeldinger = database.avbrytPlanlagteMeldingerVedDodsfall(listOf(fnr), OffsetDateTime.now(ZoneOffset.UTC))
        if (antallAvbrutteMeldinger > 0) {
            log.info("Avbrøt $antallAvbrutteMeldinger melding(er) fordi bruker er død, $meldingId")
            AVBRUTT_MELDING.inc(antallAvbrutteMeldinger.toDouble())
        }
    }
}
