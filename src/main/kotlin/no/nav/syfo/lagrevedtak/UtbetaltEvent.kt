package no.nav.syfo.lagrevedtak

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UtbetaltEvent(
    val utbetalteventid: UUID,
    val startdato: LocalDate,
    val aktorid: String,
    val fnr: String,
    val organisasjonsnummer: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenstaendeSykedager: Int,
    val opprettet: LocalDateTime,
    val maksdato: LocalDate,
    val utbetalingId: UUID
)
