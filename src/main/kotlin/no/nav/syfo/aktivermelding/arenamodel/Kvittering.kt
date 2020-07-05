package no.nav.syfo.aktivermelding.arenamodel

data class Kvittering(
    val copyId: String = "K278M890", // lengde 8
    val aksjon: String = "Kvittering ", // lengde 11
    val kilde: String = "Arena", // lengde 5
    val mlen: String = "00227", // lengde 5, totallengde
    val dato: String, // lengde 8
    val klokke: String, // lengde 6
    val fnr: String, // lengde 11
    val statusOk: String, // lengde 1, J hvis ok
    val feilkode: String, // lengde 8
    val feilmelding: String // lengde 100
)

fun tilKvittering(kvitteringsmelding: String): Kvittering {
    return Kvittering(
        dato = kvitteringsmelding.substring(29, 37),
        klokke = kvitteringsmelding.substring(37, 43),
        fnr = kvitteringsmelding.substring(43, 54),
        statusOk = kvitteringsmelding[54].toString(),
        feilkode = kvitteringsmelding.substring(55, 63),
        feilmelding = kvitteringsmelding.substring(63)
    )
}
