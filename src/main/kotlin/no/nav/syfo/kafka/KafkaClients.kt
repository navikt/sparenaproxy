package no.nav.syfo.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig
import no.nav.syfo.Environment
import no.nav.syfo.kafka.aiven.KafkaUtils
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment) {
    val personhendelserKafkaConsumer = getPersonhendelserKafkaConsumer(env)
    val aivenKafkaConsumer = getAivenKafkaConsumer(env)

    private fun getPersonhendelserKafkaConsumer(
        env: Environment
    ): KafkaConsumer<String, GenericRecord> {
        val properties =
            KafkaUtils.getAivenKafkaConfig("person-hendelse-consumer")
                .apply {
                    setProperty(
                        KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG,
                        env.schemaRegistryUrl
                    )
                    setProperty(
                        KafkaAvroSerializerConfig.USER_INFO_CONFIG,
                        "${env.kafkaSchemaRegistryUsername}:${env.kafkaSchemaRegistryPassword}"
                    )
                    setProperty(
                        KafkaAvroSerializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE,
                        "USER_INFO"
                    )
                }
                .toConsumerConfig(
                    env.applicationName,
                    valueDeserializer = KafkaAvroDeserializer::class
                )
                .also {
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it["specific.avro.reader"] = false
                }

        val personhendelserKafkaConsumer = KafkaConsumer<String, GenericRecord>(properties)
        personhendelserKafkaConsumer.subscribe(listOf(env.pdlTopic))
        return personhendelserKafkaConsumer
    }

    private fun getAivenKafkaConsumer(env: Environment): KafkaConsumer<String, String> {
        val properties =
            KafkaUtils.getAivenKafkaConfig("kafka-consumer")
                .toConsumerConfig(
                    env.applicationName,
                    valueDeserializer = StringDeserializer::class
                )
                .also {
                    it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                    it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
                }

        return KafkaConsumer(properties)
    }
}
