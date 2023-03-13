package no.nav.syfo.testutil

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
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

fun opprettPlanlagtMelding(
    id: UUID,
    fnr: String = "fnr",
    sendes: OffsetDateTime = OffsetDateTime.of(2023, 1, 2, 0, 0, 0, 0, ZoneOffset.UTC).minusMinutes(30),
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
            behandletTidspunkt = LocalDateTime.now(Clock.tickMillis(ZoneId.systemDefault())),
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
            signaturDato = LocalDateTime.now(Clock.tickMillis(ZoneId.systemDefault())),
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
        mottattDato = LocalDateTime.now(Clock.tickMillis(ZoneId.systemDefault())),
        navLogId = "1",
        personNrLege = "12345678901",
        personNrPasient = fnr,
        rulesetVersion = null,
        tlfPasient = null,
        tssid = null,
        merknader = null,
        partnerreferanse = "",
        legeHelsepersonellkategori = null,
        legeHprNr = null,
        vedlegg = null,
        utenlandskSykmelding = null
    )
}

fun lagUtbetaltEvent(
    id: UUID,
    startdato: LocalDate,
    fnr: String,
    tom: LocalDate = LocalDate.of(2020, 6, 29),
    gjenstaendeSykedager: Int = 300,
    maksdato: LocalDate = LocalDate.now().plusDays(gjenstaendeSykedager.toLong())
): UtbetaltEvent =
    UtbetaltEvent(
        utbetalteventid = id,
        startdato = startdato,
        aktorid = "aktorid",
        fnr = fnr,
        organisasjonsnummer = "orgnummer",
        fom = startdato,
        tom = tom,
        forbrukteSykedager = 0,
        gjenstaendeSykedager = gjenstaendeSykedager,
        opprettet = LocalDateTime.now(Clock.tickMillis(ZoneId.systemDefault())),
        maksdato = maksdato,
        utbetalingId = UUID.randomUUID()
    )
