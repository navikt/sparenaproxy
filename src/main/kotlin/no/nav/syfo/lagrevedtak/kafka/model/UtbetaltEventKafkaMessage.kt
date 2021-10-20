package no.nav.syfo.lagrevedtak.kafka.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.lagrevedtak.Utbetalt

data class UtbetaltEventKafkaMessage(
    val utbetalteventid: UUID,
    val aktorid: String,
    val fnr: String,
    val organisasjonsnummer: String,
    val hendelser: Set<UUID>,
    val oppdrag: List<Utbetalt>,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenstaendeSykedager: Int,
    val opprettet: LocalDateTime,
    val maksdato: LocalDate?
)
