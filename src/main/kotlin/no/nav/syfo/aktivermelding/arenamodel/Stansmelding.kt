package no.nav.syfo.aktivermelding.arenamodel

import no.nav.syfo.log

data class Stansmelding(
    val k278M810: K278M810Stans,
    val k278M815: K278M815Stans,
    val k278M830: K278M830Stans,
    val k278M840: K278M840Stans
)

data class K278M810Stans(
    val copyId: String = "K278M810", // lengde 8
    val aksjon: String = "SENDMELDING", // lengde 11
    val kilde: String = "SPEIL", // lengde 5
    val brukerId: String = "".padEnd(8, ' '), // lengde 8, brukes ikke
    val mlen: String = "00482", // lengde 5, totallengde
    val dato: String, // lengde 8, dagens dato
    val klokke: String, // lengde 6, klokkeslett nå
    val navKontor: String = "".padEnd(4, ' '), // lengde 4, brukes ikke
    val fnr: String, // lengde 11
    val spesRolle: String = " ", // lengde 1, brukes ikke
    val navAnsatt: String = " ", // lengde 1, brukes ikke
    val ytelse: String = "SP", // lengde 2
    val meldKode: String = "O", // lengde 1
    val uaktuell: String = " " // lengde 1
)

data class K278M815Stans(
    val copyId: String = "K278M815", // lengde 8
    val antall: String = "00001", // lengde 5
    val fornavn: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val mellomnavn: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val etternavn: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val adresse1: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val adresse2: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val adresse3: String = "".padEnd(30, ' '), // lengde 30, brukes ikke
    val postnr: String = "".padEnd(4, ' '), // lengde 4, brukes ikke
    val bokommune: String = "".padEnd(4, ' ') // lengde 4, brukes ikke
)

data class K278M830Stans(
    val copyId: String = "K278M830", // lengde 8
    val antall: String = "00001", // lengde 5
    val meldingId: String = "M-SPUB-1".padEnd(10, ' '), // lengde 10
    val versjon: String = "005", // lengde 3
    val startdato: String, // lengde 8
    val stanskode: String = "AA", // lengde 2
    val stanstekst: String = "AVSLUTTET".padEnd(50, ' '), // lengde 50
    val filler: String = "".padEnd(30, ' ') // lengde 30
)

data class K278M840Stans(
    val copyId: String = "K278M840", // lengde 8
    val antall: String = "00001", // lengde 5
    val taglinje: String = "SP: SP sykepenger er stanset".padEnd(80, ' ') // lengde 80
)

fun Stansmelding.tilMqMelding(): String {
    val sb = StringBuilder()
    sb.append(k278M810.tilTekst())
    sb.append(k278M815.tilTekst())
    sb.append(k278M830.tilTekst())
    sb.append(k278M840.tilTekst())

    val stansmeldingSomTekst = sb.toString()
    if (stansmeldingSomTekst.length != 482) {
        log.error("Stansmelding har feil lengde: ${stansmeldingSomTekst.length}")
        throw IllegalStateException("Stansmelding har feil lengde")
    }
    return stansmeldingSomTekst
}

fun K278M810Stans.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(aksjon)
    sb.append(kilde)
    sb.append(brukerId)
    sb.append(mlen)
    sb.append(dato)
    sb.append(klokke)
    sb.append(navKontor)
    sb.append(fnr)
    sb.append(spesRolle)
    sb.append(navAnsatt)
    sb.append(ytelse)
    sb.append(meldKode)
    sb.append(uaktuell)

    val k278M810SomTekst = sb.toString()
    if (k278M810SomTekst.length != 72) {
        log.error("K278M810 (stans) har feil lengde: ${k278M810SomTekst.length}")
        throw IllegalStateException("K278M810 har feil lengde")
    }
    return k278M810SomTekst
}

fun K278M815Stans.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(fornavn)
    sb.append(mellomnavn)
    sb.append(etternavn)
    sb.append(adresse1)
    sb.append(adresse2)
    sb.append(adresse3)
    sb.append(postnr)
    sb.append(bokommune)

    val k278M815SomTekst = sb.toString()
    if (k278M815SomTekst.length != 201) {
        log.error("K278M815 (stans) har feil lengde: ${k278M815SomTekst.length}")
        throw IllegalStateException("K278M815 har feil lengde")
    }
    return k278M815SomTekst
}

fun K278M830Stans.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(meldingId)
    sb.append(versjon)
    sb.append(startdato)
    sb.append(stanskode)
    sb.append(stanstekst)
    sb.append(filler)

    val k278M830SomTekst = sb.toString()
    if (k278M830SomTekst.length != 116) {
        log.error("K278M830 (stans) har feil lengde: ${k278M830SomTekst.length}")
        throw IllegalStateException("K278M830 har feil lengde")
    }
    return k278M830SomTekst
}

fun K278M840Stans.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(taglinje)

    val k278M840SomTekst = sb.toString()
    if (k278M840SomTekst.length != 93) {
        log.error("K278M840 (stans) har feil lengde: ${k278M840SomTekst.length}")
        throw IllegalStateException("K278M840 har feil lengde")
    }
    return k278M840SomTekst
}
