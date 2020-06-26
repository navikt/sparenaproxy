package no.nav.syfo.util

import no.nav.syfo.Environment
import no.nav.syfo.VaultSecrets
import no.nav.syfo.kafka.envOverrides
import no.nav.syfo.kafka.loadBaseConfig
import no.nav.syfo.kafka.toConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer

class KafkaClients(env: Environment, vaultSecrets: VaultSecrets) {
    val kafkaUtbetaltEventConsumer = getKafkaUtbetaltEventConsumer(vaultSecrets, env)

    private fun getKafkaUtbetaltEventConsumer(vaultSecrets: VaultSecrets, env: Environment): KafkaConsumer<String, String> {
        val kafkaBaseConfig = loadBaseConfig(env, vaultSecrets).envOverrides()
        kafkaBaseConfig["auto.offset.reset"] = "latest"

        val properties = kafkaBaseConfig.toConsumerConfig("${env.applicationName}-consumer", valueDeserializer = StringDeserializer::class)
        properties.let { it[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] = "1" }

        val kafkaUtbetaltEventConsumer = KafkaConsumer<String, String>(properties)
        kafkaUtbetaltEventConsumer.subscribe(listOf(env.utbetaltEventTopic))
        return kafkaUtbetaltEventConsumer
    }
}
