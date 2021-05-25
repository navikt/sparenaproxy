package no.nav.syfo.testutil

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.lagrevedtak.Utbetalingslinje
import no.nav.syfo.lagrevedtak.Utbetalt
import no.nav.syfo.lagrevedtak.UtbetaltEvent
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.Adresse
import no.nav.syfo.model.Arbeidsgiver
import no.nav.syfo.model.AvsenderSystem
import no.nav.syfo.model.Behandler
import no.nav.syfo.model.HarArbeidsgiver
import no.nav.syfo.model.KontaktMedPasient
import no.nav.syfo.model.MedisinskVurdering
import no.nav.syfo.model.Periode
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.ReceivedSykmelding
import no.nav.syfo.model.Sykmelding

fun opprettPlanlagtMelding(
    id: UUID,
    fnr: String = "fnr",
    sendes: OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30),
    type: String = AKTIVITETSKRAV_8_UKER_TYPE,
    avbrutt: OffsetDateTime? = null,
    sendt: OffsetDateTime? = null,
    startdato: LocalDate = LocalDate.of(2020, 1, 14),
    jmsCorrelationId: String? = null
): PlanlagtMeldingDbModel {
    return PlanlagtMeldingDbModel(
        id = id,
        fnr = fnr,
        startdato = startdato,
        opprettet = OffsetDateTime.now(ZoneOffset.UTC).minusWeeks(1),
        type = type,
        sendes = sendes,
        avbrutt = avbrutt,
        sendt = sendt,
        jmsCorrelationId = jmsCorrelationId
    )
}

fun opprettReceivedSykmelding(fnr: String, perioder: List<Periode>): ReceivedSykmelding {
    return ReceivedSykmelding(
        sykmelding = Sykmelding(
            id = UUID.randomUUID().toString(),
            behandletTidspunkt = LocalDateTime.now(),
            behandler = Behandler(
                fornavn = "fornavn",
                adresse = Adresse(null, null, null, null, null),
                fnr = "12345678901",
                etternavn = "etternavn",
                aktoerId = "aktorId",
                her = null,
                tlf = null,
                hpr = null,
                mellomnavn = null
            ),
            arbeidsgiver = Arbeidsgiver(HarArbeidsgiver.EN_ARBEIDSGIVER, null, null, null),
            andreTiltak = null,
            avsenderSystem = AvsenderSystem("avsender", "1"),
            kontaktMedPasient = KontaktMedPasient(LocalDate.now(), null),
            medisinskVurdering = MedisinskVurdering(null, emptyList(), false, false, null, null),
            meldingTilArbeidsgiver = null,
            meldingTilNAV = null,
            msgId = "1",
            navnFastlege = null,
            pasientAktoerId = "1234",
            perioder = perioder,
            prognose = null,
            signaturDato = LocalDateTime.now(),
            skjermesForPasient = false,
            syketilfelleStartDato = LocalDate.now(),
            tiltakArbeidsplassen = null,
            tiltakNAV = null,
            utdypendeOpplysninger = emptyMap()
        ),
        msgId = "1",
        fellesformat = "",
        legekontorHerId = null,
        legekontorOrgName = "navn",
        legekontorOrgNr = null,
        legekontorReshId = null,
        mottattDato = LocalDateTime.now(),
        navLogId = "1",
        personNrLege = "12345678901",
        personNrPasient = fnr,
        rulesetVersion = null,
        tlfPasient = null,
        tssid = null,
        merknader = null
    )
}

fun lagUtbetaltEvent(id: UUID, sykmeldingId: UUID, startdato: LocalDate, fnr: String, tom: LocalDate = LocalDate.of(2020, 6, 29), gjenstaendeSykedager: Int = 300): UtbetaltEvent =
    UtbetaltEvent(
        utbetalteventid = id,
        startdato = startdato,
        sykmeldingid = sykmeldingId,
        aktorid = "aktorid",
        fnr = fnr,
        organisasjonsnummer = "orgnummer",
        hendelser = listOf(UUID.randomUUID(), UUID.randomUUID()).toSet(),
        oppdrag = lagOppdragsliste(),
        fom = startdato,
        tom = tom,
        forbrukteSykedager = 0,
        gjenstaendeSykedager = gjenstaendeSykedager,
        opprettet = LocalDateTime.now()
    )

fun lagOppdragsliste(): List<Utbetalt> {
    return listOf(
        Utbetalt(
            mottaker = "mottaker",
            fagomrade = "sykepenger",
            fagsystemId = "id",
            totalbelop = 6000,
            utbetalingslinjer = lagUbetalingslinjeliste()
        )
    )
}

fun lagUbetalingslinjeliste(): List<Utbetalingslinje> {
    return listOf(
        Utbetalingslinje(
            fom = LocalDate.of(2020, 6, 1),
            tom = LocalDate.of(2020, 6, 29),
            dagsats = 500,
            belop = 2000,
            grad = 70.0,
            sykedager = 20
        )
    )
}
