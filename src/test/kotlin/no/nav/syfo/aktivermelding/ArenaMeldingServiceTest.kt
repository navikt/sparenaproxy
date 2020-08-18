package no.nav.syfo.aktivermelding

import io.mockk.mockk
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_39_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object ArenaMeldingServiceTest : Spek({
    val arenaMqProducer = mockk<ArenaMqProducer>(relaxed = true)

    val arenaMeldingService = ArenaMeldingService(arenaMqProducer)

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

        aktivitetskrav8UkerMelding.n2810.dato shouldEqual "02072020"
        aktivitetskrav8UkerMelding.n2810.klokke shouldEqual "152000"
        aktivitetskrav8UkerMelding.n2810.fnr shouldEqual "12345678910"
        aktivitetskrav8UkerMelding.n2810.meldKode shouldEqual "O"
        aktivitetskrav8UkerMelding.n2830.meldingId shouldEqual "M-RK68-1  "
        aktivitetskrav8UkerMelding.n2830.versjon shouldEqual "008"
        aktivitetskrav8UkerMelding.n2830.meldingsdata shouldEqual "02052020                                                                                  " // lengde 90
        aktivitetskrav8UkerMelding.n2840.taglinje shouldEqual "SP: Aktivitetskrav ved 8 uker 100% sykmeldt                                     " // lengde 80
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

        brev39Ukersmelding.n2810.dato shouldEqual "02072020"
        brev39Ukersmelding.n2810.klokke shouldEqual "152000"
        brev39Ukersmelding.n2810.fnr shouldEqual "12345678910"
        brev39Ukersmelding.n2810.meldKode shouldEqual "I"
        brev39Ukersmelding.n2830.meldingId shouldEqual "M-F226-1  "
        brev39Ukersmelding.n2830.versjon shouldEqual "015"
        brev39Ukersmelding.n2830.meldingsdata shouldEqual "02052020                                                                                  " // lengde 90
        brev39Ukersmelding.n2840.taglinje shouldEqual "SP: 39 ukersbrevet er dannet. Brevet sendes fra Arena (via denne hendelsen).    " // lengde 80
    }

    describe("Test av datoformattering") {
        it("FormatDate formatterer dato riktig") {
            val dato = LocalDate.of(2020, 3, 12)

            val formattertDato = arenaMeldingService.formatDate(dato)

            formattertDato shouldEqual "12032020"
        }

        it("FormatDateTime formatterer dato med klokkeslett riktig") {
            val dato = OffsetDateTime.of(LocalDate.of(2020, 3, 12).atTime(15, 20), ZoneOffset.UTC)

            val formattertDato = arenaMeldingService.formatDateTime(dato)

            formattertDato shouldEqual "12032020,152000"
        }
    }
})
