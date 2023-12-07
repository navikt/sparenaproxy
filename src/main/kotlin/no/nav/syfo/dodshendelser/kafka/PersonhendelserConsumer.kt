package no.nav.syfo.dodshendelser.kafka

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class PersonhendelserConsumer(
    private val kafkaPersonhendelserConsumer: KafkaConsumer<String, GenericRecord>
) {
    fun poll(): List<GenericRecord> {
        return kafkaPersonhendelserConsumer.poll(10.seconds.toJavaDuration()).map { it.value() }
    }
}
