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
    val hendelser: Set<UUID>,
    val oppdrag: List<Utbetalt>,
    val fom: LocalDate,
    val tom: LocalDate,
    val forbrukteSykedager: Int,
    val gjenstaendeSykedager: Int,
    val opprettet: LocalDateTime,
    val maksdato: LocalDate?,
    val utbetalingId: UUID?,
    val utbetalingFom: LocalDate?,
    val utbetalingTom: LocalDate?
)

data class Utbetalt(
    val mottaker: String,
    val fagomrade: String,
    val fagsystemId: String,
    val totalbelop: Int,
    val utbetalingslinjer: List<Utbetalingslinje>
)

data class Utbetalingslinje(
    val fom: LocalDate,
    val tom: LocalDate,
    val dagsats: Int,
    val belop: Int,
    val grad: Double,
    val sykedager: Int
)
