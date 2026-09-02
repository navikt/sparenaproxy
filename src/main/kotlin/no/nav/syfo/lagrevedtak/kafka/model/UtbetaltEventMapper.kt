package no.nav.syfo.lagrevedtak.kafka.model

import java.time.LocalDate
import java.util.UUID
import tools.jackson.databind.JsonNode

fun tilUtbetaltEventKafkaMessage(node: JsonNode): UtbetaltEventKafkaMessage {
    return UtbetaltEventKafkaMessage(
        utbetalteventid = UUID.randomUUID(),
        aktorid = node["aktørId"].stringValue(),
        fnr = node["fødselsnummer"].stringValue(),
        organisasjonsnummer = node["organisasjonsnummer"].stringValue(),
        fom = LocalDate.parse(node["fom"].stringValue()),
        tom = LocalDate.parse(node["tom"].stringValue()),
        forbrukteSykedager = node["forbrukteSykedager"].asInt(),
        gjenstaendeSykedager = node["gjenståendeSykedager"].asInt(),
        maksdato = LocalDate.parse(node["foreløpigBeregnetSluttPåSykepenger"].stringValue()),
        utbetalingId = UUID.fromString(node["utbetalingId"].stringValue()),
    )
}
