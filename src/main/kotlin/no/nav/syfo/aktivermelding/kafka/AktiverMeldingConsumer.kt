package no.nav.syfo.aktivermelding.kafka

import java.time.Duration
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import org.apache.kafka.clients.consumer.KafkaConsumer

class AktiverMeldingConsumer(
    private val kafkaAktiverMeldingConsumer: KafkaConsumer<String, AktiverMelding>
) {
    fun poll(): List<AktiverMelding> {
        return kafkaAktiverMeldingConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }
}
