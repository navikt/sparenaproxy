CREATE TABLE utbetaltevent
(
    utbetalteventid UUID PRIMARY KEY,
    startdato DATE NOT NULL,
    sykmeldingid UUID NOT NULL,
    aktorid VARCHAR NOT NULL,
    fnr VARCHAR NOT NULL,
    organisasjonsnummer VARCHAR NOT NULL,
    hendelser JSONB NOT NULL,
    oppdrag JSONB NOT NULL,
    fom DATE NOT NULL,
    tom DATE NOT NULL,
    forbrukte_sykedager INT NOT NULL,
    gjenstaende_sykedager INT NOT NULL,
    opprettet TIMESTAMP NOT NULL
);

CREATE TABLE planlagt_melding
(
    id UUID PRIMARY KEY,
    fnr VARCHAR NOT NULL,
    startdato DATE NOT NULL,
    type VARCHAR NOT NULL,
    opprettet timestamptz NOT NULL,
    sendes timestamptz NOT NULL,
    avbrutt timestamptz,
    sendt timestamptz
);
