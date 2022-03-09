package no.nav.syfo.lagrevedtak.kafka.model

import java.time.LocalDate
import java.util.UUID

data class UtbetaltEventKafkaMessage(
    val utbetalteventid: UUID,
    val aktorid: String,
    val fnr: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenstaendeSykedager: Int,
    val maksdato: LocalDate,
    val utbetalingId: UUID
)
