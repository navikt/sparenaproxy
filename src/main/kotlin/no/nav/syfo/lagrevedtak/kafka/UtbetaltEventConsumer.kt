package no.nav.syfo.lagrevedtak.kafka

import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer

class UtbetaltEventConsumer(
    private val kafkaUtbetaltEventConsumer: KafkaConsumer<String, JsonNode>
) {
    fun poll(): List<JsonNode> {
        return kafkaUtbetaltEventConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }
}
