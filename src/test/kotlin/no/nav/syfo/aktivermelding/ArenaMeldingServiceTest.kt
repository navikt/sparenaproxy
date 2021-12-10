package no.nav.syfo.aktivermelding

import io.mockk.mockk
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

object ArenaMeldingServiceTest : Spek({
    val arenaMqProducer = mockk<ArenaMqProducer>(relaxed = true)

    val arenaMeldingService = ArenaMeldingService(arenaMqProducer)

    describe("Test av oppretting av 4-ukersmelding") {
        val now = OffsetDateTime.of(LocalDate.of(2020, 7, 2).atTime(15, 20), ZoneOffset.UTC)
        val planlagtMeldingDbModel = PlanlagtMeldingDbModel(
            id = UUID.randomUUID(),
            fnr = "12345678910",
            startdato = LocalDate.of(2020, 5, 2),
            type = BREV_4_UKER_TYPE,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(8),
            sendes = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        )

        val brev4Ukersmelding = arenaMeldingService.til4Ukersmelding(planlagtMeldingDbModel, now)

        brev4Ukersmelding.n2810.dato shouldBeEqualTo "02072020"
        brev4Ukersmelding.n2810.klokke shouldBeEqualTo "152000"
        brev4Ukersmelding.n2810.fnr shouldBeEqualTo "12345678910"
        brev4Ukersmelding.n2810.meldKode shouldBeEqualTo "I"
        brev4Ukersmelding.n2830.meldingId shouldBeEqualTo "M-F234-1  "
        brev4Ukersmelding.n2830.versjon shouldBeEqualTo "014"
        brev4Ukersmelding.n2830.meldingsdata shouldBeEqualTo "02052020                                                                                  " // lengde 90
        brev4Ukersmelding.n2840.taglinje shouldBeEqualTo "SP: 4 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).     " // lengde 80
    }

    describe("Test av oppretting av 8-ukersmelding") {
        val now = OffsetDateTime.of(LocalDate.of(2020, 7, 2).atTime(15, 20), ZoneOffset.UTC)
        val planlagtMeldingDbModel = PlanlagtMeldingDbModel(
            id = UUID.randomUUID(),
            fnr = "12345678910",
            startdato = LocalDate.of(2020, 5, 2),
            type = AKTIVITETSKRAV_8_UKER_TYPE,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(8),
            sendes = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        )

        val aktivitetskrav8UkerMelding = arenaMeldingService.til8Ukersmelding(planlagtMeldingDbModel, now)

        aktivitetskrav8UkerMelding.n2810.dato shouldBeEqualTo "02072020"
        aktivitetskrav8UkerMelding.n2810.klokke shouldBeEqualTo "152000"
        aktivitetskrav8UkerMelding.n2810.fnr shouldBeEqualTo "12345678910"
        aktivitetskrav8UkerMelding.n2810.meldKode shouldBeEqualTo "O"
        aktivitetskrav8UkerMelding.n2830.meldingId shouldBeEqualTo "M-RK68-1  "
        aktivitetskrav8UkerMelding.n2830.versjon shouldBeEqualTo "008"
        aktivitetskrav8UkerMelding.n2830.meldingsdata shouldBeEqualTo "02052020                                                                                  " // lengde 90
        aktivitetskrav8UkerMelding.n2840.taglinje shouldBeEqualTo "SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 80
    }

    describe("Test av oppretting av 39-ukersmelding") {
        val now = OffsetDateTime.of(LocalDate.of(2020, 7, 2).atTime(15, 20), ZoneOffset.UTC)
        val planlagtMeldingDbModel = PlanlagtMeldingDbModel(
            id = UUID.randomUUID(),
            fnr = "12345678910",
            startdato = LocalDate.of(2020, 5, 2),
            type = BREV_39_UKER_TYPE,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(8),
            sendes = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        )

        val brev39Ukersmelding = arenaMeldingService.til39Ukersmelding(planlagtMeldingDbModel, now)

        brev39Ukersmelding.n2810.dato shouldBeEqualTo "02072020"
        brev39Ukersmelding.n2810.klokke shouldBeEqualTo "152000"
        brev39Ukersmelding.n2810.fnr shouldBeEqualTo "12345678910"
        brev39Ukersmelding.n2810.meldKode shouldBeEqualTo "I"
        brev39Ukersmelding.n2830.meldingId shouldBeEqualTo "M-F226-1  "
        brev39Ukersmelding.n2830.versjon shouldBeEqualTo "015"
        brev39Ukersmelding.n2830.meldingsdata shouldBeEqualTo "02052020                                                                                  " // lengde 90
        brev39Ukersmelding.n2840.taglinje shouldBeEqualTo "SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).    " // lengde 80
    }

    describe("Test av oppretting av stansmelding") {
        val now = OffsetDateTime.of(LocalDate.of(2020, 7, 2).atTime(15, 20), ZoneOffset.UTC)
        val planlagtMeldingDbModel = PlanlagtMeldingDbModel(
            id = UUID.randomUUID(),
            fnr = "12345678910",
            startdato = LocalDate.of(2020, 5, 2),
            type = STANS_TYPE,
            opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(8),
            sendes = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10)
        )

        val stansmelding = arenaMeldingService.tilStansmelding(planlagtMeldingDbModel, now)

        stansmelding.k278M810.dato shouldBeEqualTo "02072020"
        stansmelding.k278M810.klokke shouldBeEqualTo "152000"
        stansmelding.k278M810.fnr shouldBeEqualTo "12345678910"
        stansmelding.k278M830.startdato shouldBeEqualTo "02052020"
    }

    describe("Test av datoformattering") {
        it("FormatDate formatterer dato riktig") {
            val dato = LocalDate.of(2020, 3, 12)

            val formattertDato = arenaMeldingService.formatDate(dato)

            formattertDato shouldBeEqualTo "12032020"
        }

        it("FormatDateTime formatterer dato med klokkeslett riktig") {
            val dato = OffsetDateTime.of(LocalDate.of(2020, 3, 12).atTime(15, 20), ZoneOffset.UTC)

            val formattertDato = arenaMeldingService.formatDateTime(dato)

            formattertDato shouldBeEqualTo "12032020,152000"
        }
    }
})
