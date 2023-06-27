package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.db.finnAktivStansmelding
import no.nav.syfo.aktivermelding.db.finnAvbrutt39ukersmelding
import no.nav.syfo.aktivermelding.db.finnAvbruttAktivitetskravmelding
import no.nav.syfo.aktivermelding.db.resendAvbruttMelding
import no.nav.syfo.aktivermelding.db.sendPlanlagtMelding
import no.nav.syfo.aktivermelding.db.utsettPlanlagtMelding
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.SENDT_AVBRUTT_MELDING
import no.nav.syfo.application.metrics.UTSATT_MELDING
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.log
import no.nav.syfo.model.Periode
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper

class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val arenaMeldingService: ArenaMeldingService,
    private val skalVenteLitt: Boolean = true
) {
    suspend fun mottaNySykmelding(record: String) {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(record)
        if (receivedSykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
            log.info(
                "Ignorerer sykmelding som er til manuell behandling ${receivedSykmelding.sykmelding.id}"
            )
        } else {
            behandleMottattSykmelding(receivedSykmelding)
        }
    }

    suspend fun behandleMottattSykmelding(receivedSykmelding: ReceivedSykmelding) {
        val sykmeldingId = receivedSykmelding.sykmelding.id

        val aktiveStansmeldinger =
            database.finnAktivStansmelding(receivedSykmelding.personNrPasient)
        val avbrutteAktivitetskravMeldinger =
            database.finnAvbruttAktivitetskravmelding(receivedSykmelding.personNrPasient)
        val avbrutte39ukersMeldinger =
            database.finnAvbrutt39ukersmelding(receivedSykmelding.personNrPasient)

        if (
            aktiveStansmeldinger.isEmpty() &&
                avbrutteAktivitetskravMeldinger.isEmpty() &&
                avbrutte39ukersMeldinger.isEmpty()
        ) {
            log.info(
                "Fant ingen relevante planlagte meldinger knyttet til sykmeldingid $sykmeldingId"
            )
            return
        }
        if (skalVenteLitt) {
            delay(5000) // Venter slik at sykmeldingen kommer inn i syfosyketilfelle...
        }
        val startdato =
            syfoSyketilfelleClient.finnStartdato(
                fnr = receivedSykmelding.personNrPasient,
                sykmeldingId = sykmeldingId,
                sporingsId = UUID.fromString(sykmeldingId)
            )

        sendAvbrutt39ukersmelding(
            receivedSykmelding,
            avbrutte39ukersMeldinger.firstOrNull { it.startdato == startdato }
        )
        utsettStansmelding(
            receivedSykmelding,
            aktiveStansmeldinger.firstOrNull { it.startdato == startdato }
        )
    }

    fun sendAvbrutt39ukersmelding(
        receivedSykmelding: ReceivedSykmelding,
        avbruttMelding: PlanlagtMeldingDbModel?
    ) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (avbruttMelding == null) {
            log.info(
                "Fant ingen matchende avbrutte 39-ukersmeldinger, ignorerer sykmelding med id {}",
                sykmeldingId
            )
        } else {
            log.info(
                "Sender 39-ukersmelding med id {} for sykmeldingid {}",
                avbruttMelding.id,
                sykmeldingId
            )
            database.resendAvbruttMelding(avbruttMelding.id)
            val correlationId = arenaMeldingService.sendPlanlagtMeldingTilArena(avbruttMelding)
            database.sendPlanlagtMelding(
                avbruttMelding.id,
                OffsetDateTime.now(ZoneOffset.UTC),
                correlationId
            )
            SENDT_AVBRUTT_MELDING.inc()
        }
    }

    fun utsettStansmelding(
        receivedSykmelding: ReceivedSykmelding,
        stansmelding: PlanlagtMeldingDbModel?
    ) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (stansmelding == null) {
            log.info(
                "Fant ingen matchende stansmeldinger, ignorerer sykmelding med id {}",
                sykmeldingId
            )
        } else {
            val oppdatertSendes =
                finnSisteTom(receivedSykmelding.sykmelding.perioder)
                    .plusDays(17)
                    .atStartOfDay()
                    .atZone(ZoneId.systemDefault())
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toOffsetDateTime()
            if (oppdatertSendes.isAfter(stansmelding.sendes)) {
                log.info(
                    "Mottatt sykmelding med nyeste tomdato senere en utsendingstidspunkt, utsetter stansmelding ${stansmelding.id}"
                )
                database.utsettPlanlagtMelding(stansmelding.id, oppdatertSendes)
                UTSATT_MELDING.inc()
            }
        }
    }

    fun inneholderGradertPeriode(perioder: List<Periode>): Boolean {
        return perioder.firstOrNull { it.gradert != null }?.gradert != null
    }

    private fun finnSisteTom(perioder: List<Periode>): LocalDate {
        return perioder.maxByOrNull { it.tom }?.tom
            ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
