package no.nav.syfo.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import no.nav.syfo.Environment
import no.nav.syfo.Serviceuser
import no.nav.syfo.kafka.aiven.KafkaUtils
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

class KafkaClients(env: Environment, vaultSecrets: Serviceuser) {
    private val baseConfig = getBaseConfig(vaultSecrets, env)

    val personhendelserKafkaConsumer = getPersonhendelserKafkaConsumer(baseConfig, env)
    val aivenKafkaConsumer = getAivenKafkaConsumer(env)

    private fun getBaseConfig(vaultSecrets: Serviceuser, env: Environment): Properties {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
        kafkaBaseConfig["auto.offset.reset"] = "none"
        kafkaBaseConfig["specific.avro.reader"] = false
        return kafkaBaseConfig
    }

    private fun getPersonhendelserKafkaConsumer(kafkaBaseConfig: Properties, env: Environment): KafkaConsumer<String, GenericRecord> {
        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val personhendelserKafkaConsumer = KafkaConsumer<String, GenericRecord>(properties)
        personhendelserKafkaConsumer.subscribe(listOf(env.pdlTopic))
        return personhendelserKafkaConsumer
    }

    private fun getAivenKafkaConsumer(env: Environment): KafkaConsumer<String, String> {
        val properties = KafkaUtils.getAivenKafkaConfig()
            .toConsumerConfig(
                "${env.applicationName}-consumer",
                valueDeserializer = StringDeserializer::class
            ).also {
                it[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "none"
                it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1"
            }

        return KafkaConsumer(properties)
    }
}
