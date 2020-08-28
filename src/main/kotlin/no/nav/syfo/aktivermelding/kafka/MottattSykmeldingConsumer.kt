package no.nav.syfo.aktivermelding.kafka

import java.time.Duration
import org.apache.kafka.clients.consumer.KafkaConsumer

class MottattSykmeldingConsumer(
    private val kafkaMottattSykmeldingConsumer: KafkaConsumer<String, String>
) {
    fun poll(): List<String> {
        return kafkaMottattSykmeldingConsumer.poll(Duration.ofMillis(0)).mapNotNull { it.value() }
    }
}
