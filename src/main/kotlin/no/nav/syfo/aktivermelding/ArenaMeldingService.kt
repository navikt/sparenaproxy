package no.nav.syfo.aktivermelding

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import no.nav.syfo.aktivermelding.arenamodel.Aktivitetskrav8UkerMelding
import no.nav.syfo.aktivermelding.arenamodel.Brev39UkerMelding
import no.nav.syfo.aktivermelding.arenamodel.Brev4UkerMelding
import no.nav.syfo.aktivermelding.arenamodel.K278M810Stans
import no.nav.syfo.aktivermelding.arenamodel.K278M815Stans
import no.nav.syfo.aktivermelding.arenamodel.K278M830Stans
import no.nav.syfo.aktivermelding.arenamodel.K278M840Stans
import no.nav.syfo.aktivermelding.arenamodel.N2810
import no.nav.syfo.aktivermelding.arenamodel.N2820
import no.nav.syfo.aktivermelding.arenamodel.N2830
import no.nav.syfo.aktivermelding.arenamodel.N2840
import no.nav.syfo.aktivermelding.arenamodel.Stansmelding
import no.nav.syfo.aktivermelding.arenamodel.tilMqMelding
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.log
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE

class ArenaMeldingService(
    private val arenaMqProducer: ArenaMqProducer
) {
    private val dateFormat = "ddMMyyyy"
    private val dateTimeFormat = "ddMMyyyy,HHmmss"

    fun sendPlanlagtMeldingTilArena(planlagtMeldingDbModel: PlanlagtMeldingDbModel): String {
        when (planlagtMeldingDbModel.type) {
            BREV_4_UKER_TYPE -> {
                return arenaMqProducer.sendTilArena(
                    til4Ukersmelding(
                        planlagtMeldingDbModel,
                        OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                    ).tilMqMelding()
                )
            }
            AKTIVITETSKRAV_8_UKER_TYPE -> {
                return arenaMqProducer.sendTilArena(
                    til8Ukersmelding(
                        planlagtMeldingDbModel,
                        OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                    ).tilMqMelding()
                ).also { log.info("Sendt melding om ${planlagtMeldingDbModel.type} til Arena, id ${planlagtMeldingDbModel.id}") }
            }
            BREV_39_UKER_TYPE -> {
                return arenaMqProducer.sendTilArena(
                    til39Ukersmelding(
                        planlagtMeldingDbModel,
                        OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                    ).tilMqMelding()
                ).also { log.info("Sendt melding om ${planlagtMeldingDbModel.type} til Arena, id ${planlagtMeldingDbModel.id}") }
            }
            STANS_TYPE -> {
                return arenaMqProducer.sendTilArena(
                    tilStansmelding(
                        planlagtMeldingDbModel,
                        OffsetDateTime.now(ZoneId.of("Europe/Oslo"))
                    ).tilMqMelding()
                ).also { log.info("Sendt melding om ${planlagtMeldingDbModel.type} til Arena, id ${planlagtMeldingDbModel.id}") }
            }
            else -> {
                log.error(
                    "Planlagt melding {} har ukjent type: {}",
                    planlagtMeldingDbModel.id,
                    planlagtMeldingDbModel.type
                )
                throw IllegalStateException("Planlagt melding har ukjent type: ${planlagtMeldingDbModel.type}")
            }
        }
    }

    fun til4Ukersmelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel, now: OffsetDateTime): Brev4UkerMelding {
        val nowFormatted = formatDateTime(now)
        return Brev4UkerMelding(
            n2810 = N2810(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = planlagtMeldingDbModel.fnr,
                meldKode = "I"
            ),
            n2820 = N2820(),
            n2830 = N2830(
                meldingId = "M-F234-1".padEnd(10, ' '),
                versjon = "014",
                meldingsdata = formatDate(planlagtMeldingDbModel.startdato).padEnd(90, ' ')
            ),
            n2840 = N2840(
                taglinje = "SP: 4 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).".padEnd(80, ' ')
            )
        )
    }

    fun til8Ukersmelding(
        planlagtMeldingDbModel: PlanlagtMeldingDbModel,
        now: OffsetDateTime
    ): Aktivitetskrav8UkerMelding {
        val nowFormatted = formatDateTime(now)
        return Aktivitetskrav8UkerMelding(
            n2810 = N2810(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = planlagtMeldingDbModel.fnr,
                meldKode = "O"
            ),
            n2820 = N2820(),
            n2830 = N2830(
                meldingId = "M-RK68-1".padEnd(10, ' '),
                versjon = "008",
                meldingsdata = formatDate(planlagtMeldingDbModel.startdato).padEnd(90, ' ')
            ),
            n2840 = N2840(
                taglinje = "SP: Aktivitetskrav ved 8 uker 100% sykmeldt".padEnd(80, ' ')
            )
        )
    }

    fun til39Ukersmelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel, now: OffsetDateTime): Brev39UkerMelding {
        val nowFormatted = formatDateTime(now)
        return Brev39UkerMelding(
            n2810 = N2810(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = planlagtMeldingDbModel.fnr,
                meldKode = "I"
            ),
            n2820 = N2820(),
            n2830 = N2830(
                meldingId = "M-F226-1".padEnd(10, ' '),
                versjon = "015",
                meldingsdata = formatDate(planlagtMeldingDbModel.startdato).padEnd(90, ' ')
            ),
            n2840 = N2840(
                taglinje = "SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).".padEnd(
                    80,
                    ' '
                )
            )
        )
    }

    fun tilStansmelding(planlagtMeldingDbModel: PlanlagtMeldingDbModel, now: OffsetDateTime): Stansmelding {
        val nowFormatted = formatDateTime(now)
        return Stansmelding(
            k278M810 = K278M810Stans(
                dato = nowFormatted.split(',')[0],
                klokke = nowFormatted.split(',')[1],
                fnr = planlagtMeldingDbModel.fnr
            ),
            k278M815 = K278M815Stans(),
            k278M830 = K278M830Stans(
                startdato = formatDate(planlagtMeldingDbModel.startdato)
            ),
            k278M840 = K278M840Stans()
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
