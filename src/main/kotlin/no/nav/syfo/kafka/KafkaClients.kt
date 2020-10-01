package no.nav.syfo.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment, vaultSecrets: VaultSecrets) {
    private val baseConfig = getBaseConfig(vaultSecrets, env)

    val kafkaConsumer = getKafkaConsumer(baseConfig, env)
    val personhendelserKafkaConsumer = getPersonhendelserKafkaConsumer(baseConfig, env)

    private fun getBaseConfig(vaultSecrets: VaultSecrets, env: Environment): Properties {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
        kafkaBaseConfig["auto.offset.reset"] = "latest"
        kafkaBaseConfig["specific.avro.reader"] = false
        return kafkaBaseConfig
    }

    private fun getKafkaConsumer(kafkaBaseConfig: Properties, env: Environment): KafkaConsumer<String, String> {
        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val kafkaConsumer = KafkaConsumer<String, String>(properties)
        kafkaConsumer.subscribe(
            listOf(
                env.utbetaltEventTopic,
                env.aktiverMeldingTopic,
                env.sykmeldingAutomatiskBehandlingTopic,
                env.sykmeldingManuellBehandlingTopic
            )
        )
        return kafkaConsumer
    }

    private fun getPersonhendelserKafkaConsumer(kafkaBaseConfig: Properties, env: Environment): KafkaConsumer<String, GenericRecord> {
        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = KafkaAvroDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val personhendelserKafkaConsumer = KafkaConsumer<String, GenericRecord>(properties)
        personhendelserKafkaConsumer.subscribe(listOf(env.pdlTopic))
        return personhendelserKafkaConsumer
    }
}
