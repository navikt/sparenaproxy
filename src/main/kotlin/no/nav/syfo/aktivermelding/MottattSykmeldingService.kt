package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.db.finnAktivStansmelding
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

@KtorExperimentalAPI
class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val arenaMeldingService: ArenaMeldingService,
    private val skalVenteLitt: Boolean = true
) {
    suspend fun mottaNySykmelding(record: String) {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(record)
        behandleMottattSykmelding(receivedSykmelding)
    }

    suspend fun behandleMottattSykmelding(receivedSykmelding: ReceivedSykmelding) {
        val sykmeldingId = receivedSykmelding.sykmelding.id

        val aktiveStansmeldinger = database.finnAktivStansmelding(receivedSykmelding.personNrPasient)
        val avbrutteAktivitetskravMeldinger = database.finnAvbruttAktivitetskravmelding(receivedSykmelding.personNrPasient)

        if (aktiveStansmeldinger.isEmpty() && avbrutteAktivitetskravMeldinger.isEmpty()) {
            log.info("Fant ingen relevante planlagte meldinger knyttet til sykmeldingid $sykmeldingId")
            return
        }
        if (skalVenteLitt) {
            delay(5000) // Venter slik at sykmeldingen kommer inn i syfosyketilfelle...
        }
        val startdato = syfoSyketilfelleClient.finnStartdato(receivedSykmelding.sykmelding.pasientAktoerId, sykmeldingId, UUID.fromString(sykmeldingId))

        sendAvbruttAktivitetskravmelding(receivedSykmelding, avbrutteAktivitetskravMeldinger.firstOrNull { it.startdato == startdato })
        utsettStansmelding(receivedSykmelding, aktiveStansmeldinger.firstOrNull { it.startdato == startdato })
    }

    // En melding avbrytes kun hvis bruker enten er frisk eller har gradert sykmelding ved 8 uker. Derfor blir det
    // riktig å sende melding hvis det kommer inn en ny sykmelding som ikke er gradert hvis det finnes avbrutt melding
    // for samme sykeforløp (samme startdato).
    fun sendAvbruttAktivitetskravmelding(receivedSykmelding: ReceivedSykmelding, avbruttMelding: PlanlagtMeldingDbModel?) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (inneholderGradertPeriode(receivedSykmelding.sykmelding.perioder)) {
            log.info("Ignorerer gradert sykmelding med id {}", sykmeldingId)
            return
        }
        if (avbruttMelding == null) {
            log.info("Fant ingen matchende avbrutte meldinger, ignorerer sykmelding med id {}", sykmeldingId)
        } else {
            log.info("Sender melding med id {} for sykmeldingid {}", avbruttMelding.id, sykmeldingId)
            database.resendAvbruttMelding(avbruttMelding.id)
            arenaMeldingService.sendPlanlagtMeldingTilArena(avbruttMelding)
            database.sendPlanlagtMelding(avbruttMelding.id, OffsetDateTime.now(ZoneOffset.UTC))
            SENDT_AVBRUTT_MELDING.inc()
        }
    }

    fun utsettStansmelding(receivedSykmelding: ReceivedSykmelding, stansmelding: PlanlagtMeldingDbModel?) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (stansmelding == null) {
            log.info("Fant ingen matchende stansmeldinger, ignorerer sykmelding med id {}", sykmeldingId)
        } else {
            val oppdatertSendes = finnSisteTom(receivedSykmelding.sykmelding.perioder).plusDays(17).atStartOfDay().atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneOffset.UTC).toOffsetDateTime()
            if (oppdatertSendes.isAfter(stansmelding.sendes)) {
                log.info("Mottatt sykmelding med nyeste tomdato senere en utsendingstidspunkt, utsetter stansmelding ${stansmelding.id}")
                database.utsettPlanlagtMelding(stansmelding.id, oppdatertSendes)
                UTSATT_MELDING.inc()
            }
        }
    }

    fun inneholderGradertPeriode(perioder: List<Periode>): Boolean {
        return perioder.firstOrNull { it.gradert != null }?.gradert != null
    }

    private fun finnSisteTom(perioder: List<Periode>): LocalDate {
        return perioder.maxBy { it.tom }?.tom ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
    }
}
