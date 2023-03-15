package no.nav.syfo.aktivermelding.arenamodel

import no.nav.syfo.log

data class Brev4UkerMelding(
    val n2810: N2810,
    val n2820: N2820,
    val n2830: N2830,
    val n2840: N2840
)

data class Brev39UkerMelding(
    val n2810: N2810,
    val n2820: N2820,
    val n2830: N2830,
    val n2840: N2840
)

data class N2810(
    val copyId: String = "N2810".padEnd(8, ' '), // lengde 8
    val aksjon: String = "SENDMELDING", // lengde 11
    val kilde: String = "SPEIL", // lengde 5
    val brukerId: String = "".padEnd(8, ' '), // lengde 8, ikke i bruk
    val mlen: String = "00482", // lengde 5, totallengde
    val dato: String, // lengde 8, dagens dato
    val klokke: String, // lengde 6, klokkeslett nå
    val navKontor: String = "".padEnd(4, ' '), // lengde 4, brukes ikke
    val fnr: String, // lengde 11
    val spesRolle: String = " ", // lengde 1, brukes ikke
    val navAnsatt: String = " ", // lengde 1, brukes ikke
    val ytelse: String = "SP", // lengde 2
    val meldKode: String, // lengde 1
    val uaktuell: String = " " // lengde 1
)

data class N2820(
    val copyId: String = "N2820".padEnd(8, ' '), // lengde 8
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

data class N2830(
    val copyId: String = "N2830".padEnd(8, ' '), // lengde 8
    val antall: String = "00001", // lengde 5
    val meldingId: String, // lengde 10
    val versjon: String, // lengde 3
    val meldingsdata: String // lengde 90: startdato (lengde 8) + filler (lengde 80)
)

data class N2840(
    val copyId: String = "N2840".padEnd(8, ' '), // lengde 8
    val antall: String = "00001", // lengde 5
    val taglinje: String // lengde 80
)

fun Brev4UkerMelding.tilMqMelding(): String {
    val sb = StringBuilder()
    sb.append(n2810.tilTekst())
    sb.append(n2820.tilTekst())
    sb.append(n2830.tilTekst())
    sb.append(n2840.tilTekst())

    val brev4ukermeldingSomTekst = sb.toString()
    if (brev4ukermeldingSomTekst.length != 482) {
        log.error("4-ukersmelding har feil lengde: ${brev4ukermeldingSomTekst.length}")
        throw IllegalStateException("4-ukersmelding har feil lengde")
    }
    return brev4ukermeldingSomTekst
}

fun Brev39UkerMelding.tilMqMelding(): String {
    val sb = StringBuilder()
    sb.append(n2810.tilTekst())
    sb.append(n2820.tilTekst())
    sb.append(n2830.tilTekst())
    sb.append(n2840.tilTekst())

    val brev39ukermeldingSomTekst = sb.toString()
    if (brev39ukermeldingSomTekst.length != 482) {
        log.error("39-ukersmelding har feil lengde: ${brev39ukermeldingSomTekst.length}")
        throw IllegalStateException("39-ukersmelding har feil lengde")
    }
    return brev39ukermeldingSomTekst
}

fun N2810.tilTekst(): String {
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

    val n2810SomTekst = sb.toString()
    if (n2810SomTekst.length != 72) {
        log.error("N2810 har feil lengde: ${n2810SomTekst.length}")
        throw IllegalStateException("N2810 har feil lengde")
    }
    return n2810SomTekst
}

fun N2820.tilTekst(): String {
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

    val n2820SomTekst = sb.toString()
    if (n2820SomTekst.length != 201) {
        log.error("N2820 har feil lengde: ${n2820SomTekst.length}")
        throw IllegalStateException("N2820 har feil lengde")
    }
    return n2820SomTekst
}

fun N2830.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(meldingId)
    sb.append(versjon)
    sb.append(meldingsdata)

    val n2830SomTekst = sb.toString()
    if (n2830SomTekst.length != 116) {
        log.error("N2830 har feil lengde: ${n2830SomTekst.length}")
        throw IllegalStateException("N2830 har feil lengde")
    }
    return n2830SomTekst
}

fun N2840.tilTekst(): String {
    val sb = StringBuilder()
    sb.append(copyId)
    sb.append(antall)
    sb.append(taglinje)

    val n2840SomTekst = sb.toString()
    if (n2840SomTekst.length != 93) {
        log.error("N2840 har feil lengde: ${n2840SomTekst.length}")
        throw IllegalStateException("N2840 har feil lengde")
    }
    return n2840SomTekst
}
