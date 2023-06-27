package no.nav.syfo.db

import java.sql.Connection
import java.time.LocalDate
import no.nav.syfo.application.db.DatabaseInterface

fun DatabaseInterface.fireukersmeldingErSendt(fnr: String, startdato: LocalDate): Boolean {
    connection.use { connection ->
        return connection.fireukersmeldingErSendt(fnr, startdato)
    }
}

private fun Connection.fireukersmeldingErSendt(fnr: String, startdato: LocalDate): Boolean =
    this.prepareStatement(
            """
            SELECT * FROM planlagt_melding WHERE fnr=? AND startdato=? AND type='4UKER' AND sendt is not null;
            """
        )
        .use {
            it.setString(1, fnr)
            it.setObject(2, startdato)
            it.executeQuery().next()
        }
