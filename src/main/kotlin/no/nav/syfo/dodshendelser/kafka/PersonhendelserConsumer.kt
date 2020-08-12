package no.nav.syfo.dodshendelser.kafka

import java.time.Duration
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

class PersonhendelserConsumer(
    private val kafkaPersonhendelserConsumer: KafkaConsumer<String, GenericRecord>
) {
    fun poll(): List<GenericRecord> {
        return kafkaPersonhendelserConsumer.poll(Duration.ofMillis(0)).map { it.value() }
    }
}
