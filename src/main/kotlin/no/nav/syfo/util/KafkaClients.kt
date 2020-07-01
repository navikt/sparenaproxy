package no.nav.syfo.util

import java.util.Properties
import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.aktivermelding.kafka.model.AktiverMelding
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment, vaultSecrets: VaultSecrets) {
    private val baseConfig = getBaseConfig(vaultSecrets, env)

    val kafkaUtbetaltEventConsumer = getKafkaUtbetaltEventConsumer(baseConfig, env)
    val kafkaAktiverMeldingConsumer = getKafkaAktiverMeldingConsumer(baseConfig, env)

    private fun getBaseConfig(vaultSecrets: VaultSecrets, env: Environment): Properties {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
        kafkaBaseConfig["auto.offset.reset"] = "latest"
        return kafkaBaseConfig
    }

    private fun getKafkaUtbetaltEventConsumer(kafkaBaseConfig: Properties, env: Environment): KafkaConsumer<String, String> {
        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val kafkaUtbetaltEventConsumer = KafkaConsumer<String, String>(properties)
        kafkaUtbetaltEventConsumer.subscribe(listOf(env.utbetaltEventTopic))
        return kafkaUtbetaltEventConsumer
    }

    private fun getKafkaAktiverMeldingConsumer(kafkaBaseConfig: Properties, env: Environment): KafkaConsumer<String, AktiverMelding> {
        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = JacksonKafkaDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val kafkaAktiverMeldingConsumer = KafkaConsumer<String, AktiverMelding>(properties)
        kafkaAktiverMeldingConsumer.subscribe(listOf(env.aktiverMeldingTopic))
        return kafkaAktiverMeldingConsumer
    }
}
