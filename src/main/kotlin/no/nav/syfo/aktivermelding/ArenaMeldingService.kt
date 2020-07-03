package no.nav.syfo.aktivermelding

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import no.nav.syfo.aktivermelding.arenamodel.Aktivitetskrav8UkerMelding
import no.nav.syfo.aktivermelding.arenamodel.N2810
import no.nav.syfo.aktivermelding.arenamodel.N2820
import no.nav.syfo.aktivermelding.arenamodel.N2830
import no.nav.syfo.aktivermelding.arenamodel.N2840
import no.nav.syfo.aktivermelding.arenamodel.tilMqMelding
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel

class ArenaMeldingService(
    private val arenaMqProducer: ArenaMqProducer
) {
    private val dateFormat = "ddMMyyyy"
    private val dateTimeFormat = "ddMMyyyy,HHmmss"

    fun sendPlanlagtMeldingTilArena(planlagtMeldingDbModel: PlanlagtMeldingDbModel) {
        if (planlagtMeldingDbModel.type == AKTIVITETSKRAV_8_UKER_TYPE) {
            arenaMqProducer.sendTilArena(til8Ukersmelding(planlagtMeldingDbModel, OffsetDateTime.now(ZoneId.of("Europe/Oslo"))).tilMqMelding())
            log.info("Sendt melding om ${planlagtMeldingDbModel.type} til Arena, id ${planlagtMeldingDbModel.id}")
        } else {
            log.error("Planlagt melding {} har ukjent type: {}", planlagtMeldingDbModel.id, planlagtMeldingDbModel.type)
            throw IllegalStateException("Planlagt melding har ukjent type: ${planlagtMeldingDbModel.type}")
        }
    }

    fun til8Ukersmelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel, now: OffsetDateTime): Aktivitetskrav8UkerMelding {
        val nowFormatted = formatDateTime(now)
        return Aktivitetskrav8UkerMelding(
            n2810 = N2810(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = planlagtMeldingDbModel.fnr
            ),
            n2820 = N2820(),
            n2830 = N2830(
                meldingsdata = formatDate(planlagtMeldingDbModel.startdato).padEnd(90, ' ')
            ),
            n2840 = N2840()
        )
    }

    fun formatDateTime(dateTime: OffsetDateTime): String {
        val formatter = DateTimeFormatter.ofPattern(dateTimeFormat)
        return dateTime.format(formatter)
    }

    fun formatDate(date: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern(dateFormat)
        return formatter.format(date)
    }
}
