package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import no.nav.syfo.mq.MqConfig

data class Environment(
        val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
        val applicationName: String = getEnvVar("NAIS_APP_NAME", "sparenaproxy"),
        override val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
        override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
        val sparenaproxyDBURL: String = getEnvVar("SPARENAPROXY_DB_URL"),
        val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
        val databaseName: String = getEnvVar("DATABASE_NAME", "sparenaproxy"),
        val utbetaltEventTopic: String = getEnvVar("UTBETALTEVENT_TOPIC", "helse-rapid-v1"),
        val utbetaltEventAivenTopic: String = getEnvVar("UTBETALTEVENT_AIVEN_TOPIC", "tbd.rapid.v1"),
        val aktiverMeldingTopic: String = getEnvVar("AKTIVER_MELDING_TOPIC", "privat-aktiver-planlagtmelding"),
        val sykmeldingManuellBehandlingTopic: String = getEnvVar("KAFKA_SM2013_MANUAL_TOPIC", "privat-syfo-sm2013-manuellBehandling"),
        val sykmeldingAutomatiskBehandlingTopic: String = getEnvVar("KAFKA_SM2013_AUTOMATIC_TOPIC", "privat-syfo-sm2013-automatiskBehandling"),
        val pdlTopic: String = getEnvVar("PDL_TOPIC", "aapen-person-pdl-leesah-v1"),
        val syketilfelleEndpointURL: String = getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://syfosyketilfelle.flex"),
        val smregisterEndpointURL: String = getEnvVar("SMREGISTER_ENDPOINT_URL", "http://syfosmregister.default"),
        val spokelseEndpointURL: String = getEnvVar("SPOKELSE_ENDPOINT_URL", "http://spokelse.tbd.svc.nais.local"),
        val clientIdSpokelse: String = getEnvVar("SPOKELSE_CLIENTID"),
        val clientIdSmregister: String = getEnvVar("SMREGISTER_CLIENTID"),
        val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL"),
        val stsUrl: String = getEnvVar("STS_URL", "http://security-token-service.default/rest/v1/sts/token"),
        override val mqHostname: String = getEnvVar("MQ_HOST_NAME"),
        override val mqPort: Int = getEnvVar("MQ_PORT").toInt(),
        override val mqGatewayName: String = getEnvVar("MQ_GATEWAY_NAME"),
        override val mqChannelName: String = getEnvVar("MQ_CHANNEL_NAME"),
        val arenaQueueName: String = getEnvVar("MQ_ARENA_QUEUE_NAME"),
        val kvitteringQueueName: String = getEnvVar("MQ_KVITTERING_QUEUE_NAME"),
        val backoutQueueName: String = getEnvVar("MQ_KVITTERING_BQ_QUEUE_NAME"),
        val pdlGraphqlPath: String = getEnvVar("PDL_GRAPHQL_PATH"),
        override val truststore: String? = getEnvVar("NAV_TRUSTSTORE_PATH"),
        override val truststorePassword: String? = getEnvVar("NAV_TRUSTSTORE_PASSWORD"),
        val aadAccessTokenV2Url: String = getEnvVar("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"), // erstatter på sikt aadAccessTokenUrl
        val clientId: String = getEnvVar("AZURE_APP_CLIENT_ID"),
        val clientSecret: String = getEnvVar("AZURE_APP_CLIENT_SECRET"),
        val spokelseScope: String = getEnvVar("SPOKELSE_SCOPE")
) : MqConfig, KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val clientId: String = getFileAsString("/secrets/azuread/sparenaproxy/client_id"),
    val clientSecret: String = getFileAsString("/secrets/azuread/sparenaproxy/client_secret"),
    val mqUsername: String = getEnvVar("MQ_USERNAME"),
    val mqPassword: String = getEnvVar("MQ_PASSWORD")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
