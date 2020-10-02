package no.nav.syfo.lagrevedtak.maksdato

import no.nav.syfo.log

data class MaksdatoMelding(
    val k278M810: K278M810,
    val k278M815: K278M815,
    val k278M830: K278M830,
    val k278M840: K278M840
)

data class K278M810(
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
    val meldKode: String = "I", // lengde 1
    val uaktuell: String = " " // lengde 1
)

data class K278M815(
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

data class K278M830(
    val copyId: String = "K278M830", // lengde 8
    val antall: String = "00001", // lengde 5
    val meldingId: String = "M-SPVT-1".padEnd(10, ' '), // lengde 10
    val versjon: String = "005", // lengde 3
    val startdato: String, // lengde 8
    val maksdato: String, // lengde 8
    val orgnummer: String, // lengde 9
    val orgnummer2: String = "".padEnd(9, ' '), // lengde 9, brukes ikke
    val orgnummer3: String = "".padEnd(9, ' '), // lengde 9, brukes ikke
    val orgnummer4: String = "".padEnd(9, ' '), // lengde 9, brukes ikke
    val filler: String = "".padEnd(38, ' ') // lengde 38
)

data class K278M840(
    val copyId: String = "K278M840", // lengde 8
    val antall: String = "00001", // lengde 5
    val taglinje: String = "SP: SP, max.dato sykepenger".padEnd(80, ' ') // lengde 80
)

fun MaksdatoMelding.tilMqMelding(): String {
    val sb = StringBuilder()
    sb.append(k278M810.tilTekst())
    sb.append(k278M815.tilTekst())
    sb.append(k278M830.tilTekst())
    sb.append(k278M840.tilTekst())

    val maksdatomeldingSomTekst = sb.toString()
    if (maksdatomeldingSomTekst.length != 482) {
        log.error("Maksdatomelding har feil lengde: ${maksdatomeldingSomTekst.length}")
        throw IllegalStateException("Maksdatomelding har feil lengde")
    }
    return maksdatomeldingSomTekst
}

fun K278M810.tilTekst(): String {
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
        log.error("K278M810 har feil lengde: ${k278M810SomTekst.length}")
        throw IllegalStateException("K278M810 har feil lengde")
    }
    return k278M810SomTekst
}

fun K278M815.tilTekst(): String {
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
        log.error("K278M815 har feil lengde: ${k278M815SomTekst.length}")
        throw IllegalStateException("K278M815 har feil lengde")
    }
    return k278M815SomTekst
}

fun K278M830.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(meldingId)
    sb.append(versjon)
    sb.append(startdato)
    sb.append(maksdato)
    sb.append(orgnummer)
    sb.append(orgnummer2)
    sb.append(orgnummer3)
    sb.append(orgnummer4)
    sb.append(filler)

    val k278M830SomTekst = sb.toString()
    if (k278M830SomTekst.length != 116) {
        log.error("K278M830 har feil lengde: ${k278M830SomTekst.length}")
        throw IllegalStateException("K278M830 har feil lengde")
    }
    return k278M830SomTekst
}

fun K278M840.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(taglinje)

    val k278M840SomTekst = sb.toString()
    if (k278M840SomTekst.length != 93) {
        log.error("K278M840 har feil lengde: ${k278M840SomTekst.length}")
        throw IllegalStateException("K278M840 har feil lengde")
    }
    return k278M840SomTekst
}
