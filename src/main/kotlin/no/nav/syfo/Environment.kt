package no.nav.syfo

import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sparenaproxy"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val utbetalingTopic: String = "tbd.utbetaling",
    val aktiverMeldingAivenTopic: String = "teamsykmelding.privat-aktiver-planlagtmelding",
    val pdlTopic: String = "pdl.leesah-v1",
    val syketilfelleEndpointURL: String = getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://flex-syketilfelle.flex"),
    val syketilfelleScope: String = getEnvVar("SYKETILLFELLE_SCOPE"),
    val smregisterEndpointURL: String = getEnvVar("SMREGISTER_URL", "http://syfosmregister"),
    val smregisterScope: String = getEnvVar("SMREGISTER_SCOPE"),
    override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
    override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
    override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
    override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
    val arenaQueueName: String = getEnvVar("MQ_ARENA_QUEUE_NAME"),
    val kvitteringQueueName: String = getEnvVar("MQ_KVITTERING_QUEUE_NAME"),
    val backoutQueueName: String = getEnvVar("MQ_KVITTERING_BQ_QUEUE_NAME"),
    val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
    val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    val clientIdV2: String = getEnvVar("AZURE_APP_CLIENT_ID"),
    val clientSecretV2: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
    val pdlScope: String = getEnvVar("PDL_SCOPE"),
    val okSykmeldingTopic: String = "teamsykmelding.ok-sykmelding",
    val manuellSykmeldingTopic: String = "teamsykmelding.manuell-behandling-sykmelding",
    val databaseUsername: String = getEnvVar("NAIS_DATABASE_USERNAME"),
    val databasePassword: String = getEnvVar("NAIS_DATABASE_PASSWORD"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_DATABASE"),
    val schemaRegistryUrl: String = getEnvVar("KAFKA_SCHEMA_REGISTRY"),
    val kafkaSchemaRegistryUsername: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_USER"),
    val kafkaSchemaRegistryPassword: String = getEnvVar("KAFKA_SCHEMA_REGISTRY_PASSWORD")
) : MqConfig {
    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }
}

data class Serviceuser(
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
