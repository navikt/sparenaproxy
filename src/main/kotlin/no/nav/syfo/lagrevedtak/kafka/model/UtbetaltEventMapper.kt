package no.nav.syfo.lagrevedtak.kafka.model

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

fun tilUtbetaltEventKafkaMessage(node: JsonNode): UtbetaltEventKafkaMessage {
    return UtbetaltEventKafkaMessage(
        utbetalteventid = UUID.randomUUID(),
        aktorid = node["aktørId"].asText(),
        fnr = node["fødselsnummer"].asText(),
        organisasjonsnummer = node["organisasjonsnummer"].asText(),
        fom = LocalDate.parse(node["fom"].textValue()),
        tom = LocalDate.parse(node["tom"].textValue()),
        forbrukteSykedager = node["forbrukteSykedager"].asInt(),
        gjenstaendeSykedager = node["gjenståendeSykedager"].asInt(),
        maksdato = LocalDate.parse(node["foreløpigBeregnetSluttPåSykepenger"].textValue()),
        utbetalingId = UUID.fromString(node["utbetalingId"].textValue())
    )
}
