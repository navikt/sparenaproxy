package no.nav.syfo.aktivermelding

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.api.trace.Span
import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
import org.postgresql.util.PSQLException

class MottattSykmeldingService(
    private val database: DatabaseInterface,
    private val syfoSyketilfelleClient: SyfoSyketilfelleClient,
    private val arenaMeldingService: ArenaMeldingService,
) {

    @WithSpan
    suspend fun mottaNySykmelding(record: String) {
        val receivedSykmelding: ReceivedSykmelding = objectMapper.readValue(record)
        if (receivedSykmelding.merknader?.any { it.type == "UNDER_BEHANDLING" } == true) {
            log.info(
                "Ignorerer sykmelding som er til manuell behandling ${receivedSykmelding.sykmelding.id}",
            )
            Span.current().addEvent("Ignorerer sykmelding som er til manuell behandling")
        } else {
            retry(4) { behandleMottattSykmelding(receivedSykmelding) }
        }
    }

    private suspend fun retry(maxRetry: Int, function: suspend () -> Unit) {
        var currentRetry = 0
        val retryBackoff = mapOf(0 to 10, 1 to 50, 2 to 100, 3 to 1000)
        while (currentRetry < maxRetry) {
            try {
                function()
                return
            } catch (e: PSQLException) {
                currentRetry++
                log.warn("Error handling received sykmelding, retrying $currentRetry", e)
                if (currentRetry == maxRetry) {
                    throw e
                }
                delay(retryBackoff[currentRetry]?.toLong() ?: 1000)
            }
        }
    }

    @WithSpan
    suspend fun behandleMottattSykmelding(receivedSykmelding: ReceivedSykmelding) {
        val sykmeldingId = receivedSykmelding.sykmelding.id

        val aktiveStansmeldinger =
            database.finnAktivStansmelding(receivedSykmelding.personNrPasient)
        val avbrutteAktivitetskravMeldinger =
            database.finnAvbruttAktivitetskravmelding(receivedSykmelding.personNrPasient)

        if (
            aktiveStansmeldinger.isEmpty() &&
                avbrutteAktivitetskravMeldinger.isEmpty()
        ) {
            log.info(
                "Fant ingen relevante planlagte meldinger knyttet til sykmeldingid $sykmeldingId",
            )
            Span.current().addEvent("Ingen relevante planlagte meldinger")
            return
        }
        val startdato =
            syfoSyketilfelleClient.getStartDatoForSykmelding(
                fnr = receivedSykmelding.personNrPasient,
                sykmeldingId = sykmeldingId,
            )

        log.info(
            "Sender ikke avbrutt 39 ukers melding $sykmeldingId",
        )

        utsettStansmelding(
            receivedSykmelding,
            aktiveStansmeldinger.firstOrNull { it.startdato == startdato },
        )
    }

    @WithSpan
    fun utsettStansmelding(
        receivedSykmelding: ReceivedSykmelding,
        stansmelding: PlanlagtMeldingDbModel?
    ) {
        val sykmeldingId = receivedSykmelding.sykmelding.id
        if (stansmelding == null) {
            log.info(
                "Fant ingen matchende stansmeldinger, ignorerer sykmelding med id {}",
                sykmeldingId,
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
                    "Mottatt sykmelding med nyeste tomdato senere en utsendingstidspunkt, utsetter stansmelding ${stansmelding.id}",
                )
                database.utsettPlanlagtMelding(stansmelding.id, oppdatertSendes)
                UTSATT_MELDING.inc()
            }
        }
    }
}

private fun finnSisteTom(perioder: List<Periode>): LocalDate {
    return perioder.maxByOrNull { it.tom }?.tom
        ?: throw IllegalStateException("Skal ikke kunne ha periode uten tom")
}
