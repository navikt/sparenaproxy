package no.nav.syfo.lagrevedtak.kafka

import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer

class UtbetaltEventConsumer(
    private val kafkaUtbetaltEventConsumer: KafkaConsumer<String, String>
) {
    fun poll(): List<String> {
        return kafkaUtbetaltEventConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }
}
