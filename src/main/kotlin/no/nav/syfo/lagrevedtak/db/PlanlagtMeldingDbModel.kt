package no.nav.syfo.lagrevedtak.db

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

const val AKTIVITETSKRAV_8_UKER_TYPE = "8UKER"

data class PlanlagtMeldingDbModel(
    val id: UUID,
    val fnr: String,
    val startdato: LocalDate,
    val type: String,
    val opprettet: OffsetDateTime,
    val sendes: OffsetDateTime,
    val avbrutt: OffsetDateTime? = null,
    val sendt: OffsetDateTime? = null
)
