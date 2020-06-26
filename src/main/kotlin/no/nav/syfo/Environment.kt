package no.nav.syfo

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "sparenaproxy"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val sparenaproxyDBURL: String = getEnvVar("SPARENAPROXY_DB_URL"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "sparenaproxy"),
    val utbetaltEventTopic: String = getEnvVar("UTBETALTEVENT_TOPIC", "helse-rapid-v1"),
    val syketilfelleEndpointURL: String = getEnvVar("SYKETILLFELLE_ENDPOINT_URL", "http://syfosyketilfelle"),
    val spokelseEndpointURL: String = getEnvVar("SPOKELSE_ENDPOINT_URL", "http://spokelse.tbd.svc.nais.local"),
    val clientIdSpokelse: String = getEnvVar("SPOKELSE_CLIENTID"),
    val aadAccessTokenUrl: String = getEnvVar("AADACCESSTOKEN_URL")
) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String = getFileAsString("/secrets/serviceuser/username"),
    val serviceuserPassword: String = getFileAsString("/secrets/serviceuser/password"),
    val clientId: String = getFileAsString("/secrets/azuread/sparenaproxy/client_id"),
    val clientSecret: String = getFileAsString("/secrets/azuread/sparenaproxy/client_secret")
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)
