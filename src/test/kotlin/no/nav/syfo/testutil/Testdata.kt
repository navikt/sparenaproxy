package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel

fun opprettPlanlagtMelding(
    id: UUID,
    fnr: String = "fnr",
    sendes: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30),
    avbrutt: OffsetDateTime? = null,
    sendt: OffsetDateTime? = null
): PlanlagtMeldingDbModel {
    return PlanlagtMeldingDbModel(
        id = id,
        fnr = fnr,
        startdato = LocalDate.of(2020, 1, 14),
        opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(1),
        type = AKTIVITETSKRAV_8_UKER_TYPE,
        sendes = sendes,
        avbrutt = avbrutt,
        sendt = sendt
    )
}
