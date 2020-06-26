package no.nav.syfo.lagrevedtak.kafka.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import no.nav.syfo.lagrevedtak.Utbetalingslinje
import no.nav.syfo.lagrevedtak.Utbetalt
import no.nav.syfo.objectMapper

fun tilUtbetaltEventKafkaMessage(node: JsonNode): UtbetaltEventKafkaMessage {
    return UtbetaltEventKafkaMessage(
        utbetalteventid = UUID.randomUUID(),
        aktorid = node["aktørId"].asText(),
        fnr = node["fødselsnummer"].asText(),
        organisasjonsnummer = node["organisasjonsnummer"].asText(),
        hendelser = node["hendelser"].toHendelser(),
        oppdrag = node["utbetalt"].toOppdrag(),
        fom = objectMapper.readValue(node["fom"].textValue(), LocalDate::class.java),
        tom = objectMapper.readValue(node["tom"].textValue(), LocalDate::class.java),
        forbrukteSykedager = node["forbrukteSykedager"].asInt(),
        gjenstaendeSykedager = node["gjenståendeSykedager"].asInt(),
        opprettet = objectMapper.readValue(node["@opprettet"].textValue(), LocalDateTime::class.java)
    )
}

private fun JsonNode.toHendelser() = map {
    UUID.fromString(it.asText())
}.toSet()

private fun JsonNode.toOppdrag() = map {
    Utbetalt(
        mottaker = it["mottaker"].asText(),
        fagomrade = it["fagområde"].asText(),
        fagsystemId = it["fagsystemId"].asText(),
        totalbelop = it["totalbeløp"].asInt(),
        utbetalingslinjer = it["utbetalingslinjer"].toUtbetalingslinjer()
    )
}

private fun JsonNode.toUtbetalingslinjer() = map {
    Utbetalingslinje(
        fom = objectMapper.readValue(it["fom"].textValue(), LocalDate::class.java),
        tom = objectMapper.readValue(it["tom"].textValue(), LocalDate::class.java),
        dagsats = it["dagsats"].asInt(),
        belop = it["beløp"].asInt(),
        grad = it["grad"].asDouble(),
        sykedager = it["sykedager"].asInt()
    )
}
