package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.util.KtorExperimentalAPI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.db.finnAvbruttAktivitetskravmelding
import no.nav.syfo.aktivermelding.db.lagrePlanlagtMelding
import no.nav.syfo.aktivermelding.kafka.MottattSykmeldingConsumer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.db.DatabaseInterface
import no.nav.syfo.application.metrics.SENDT_AVBRUTT_MELDING
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.log
import no.nav.syfo.model.Periode
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.objectMapper

@KtorExperimentalAPI
class MottattSykmeldingService(
    private val applicationState: ApplicationState,
    private val mottattSykmeldingConsumer: MottattSykmeldingConsumer,
    private val database: DatabaseInterface,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val arenaMeldingService: ArenaMeldingService
) {
    suspend fun start() {
        while (applicationState.ready) {
            val mottatteSykmeldinger = mottattSykmeldingConsumer.poll()
            mottatteSykmeldinger.forEach {
                val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(it)
                behandleMottattSykmelding(receivedSykmelding)
            }
            delay(1)
        }
    }

    // En melding avbrytes kun hvis bruker enten er frisk eller har gradert sykmelding ved 8 uker. Derfor blir det
    // riktig å sende melding hvis det kommer inn en ny sykmelding som ikke er gradert hvis det finnes avbrutt melding
    // for samme sykeforløp (samme startdato).
    suspend fun behandleMottattSykmelding(receivedSykmelding: ReceivedSykmelding) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (inneholderGradertPeriode(receivedSykmelding.sykmelding.perioder)) {
            log.info("Ignorerer gradert sykmelding med id {}", sykmeldingId)
            return
        }
        val avbruttMelding = finnAvbruttMeldingForSykeforloep(
            fnr = receivedSykmelding.personNrPasient,
            aktorId = receivedSykmelding.sykmelding.pasientAktoerId,
            sykmeldingId = sykmeldingId)
        if (avbruttMelding == null) {
            log.info("Fant ingen matchende avbrutte meldinger, ignorerer sykmelding med id {}", sykmeldingId)
        } else {
            val id = UUID.randomUUID()
            val planlagtMelding = avbruttMelding.copy(
                id = id,
                opprettet = OffsetDateTime.now(ZoneOffset.UTC),
                sendes = OffsetDateTime.now(ZoneOffset.UTC),
                avbrutt = null
            )
            log.info("Lagrer og sender melding med id {} for sykmeldingid {}", id, sykmeldingId)
            database.lagrePlanlagtMelding(planlagtMelding)
            arenaMeldingService.sendPlanlagtMeldingTilArena(planlagtMelding)
            SENDT_AVBRUTT_MELDING.inc()
        }
    }

    fun inneholderGradertPeriode(perioder: List<Periode>): Boolean {
        return perioder.firstOrNull { it.gradert != null }?.gradert != null
    }

    suspend fun finnAvbruttMeldingForSykeforloep(fnr: String, aktorId: String, sykmeldingId: String): PlanlagtMeldingDbModel? {
        val avbrutteAktivitetskravMeldinger = database.finnAvbruttAktivitetskravmelding(fnr)
        if (avbrutteAktivitetskravMeldinger.isEmpty()) {
            log.info("Ignorerer sykmelding med id {} som det ikke finnes avbrutte meldinger for", sykmeldingId)
            return null
        }
        val startdato = syfoSyketilfelleClient.finnStartdato(aktorId, sykmeldingId, UUID.fromString(sykmeldingId))
        return avbrutteAktivitetskravMeldinger.firstOrNull { it.startdato == startdato }
    }
}
