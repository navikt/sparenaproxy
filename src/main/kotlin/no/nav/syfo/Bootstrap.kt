package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.apache.Apache
import io.ktor.client.engine.apache.ApacheEngineConfig
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import java.net.ProxySelector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.VaultCredentialService
import no.nav.syfo.application.vault.RenewVaultService
import no.nav.syfo.client.AccessTokenClient
import no.nav.syfo.client.StsOidcClient
import no.nav.syfo.lagrevedtak.LagreUtbetaltEventOgPlanlagtMeldingService
import no.nav.syfo.lagrevedtak.VedtakService
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.kafka.UtbetaltEventConsumer
import no.nav.syfo.util.KafkaClients
import org.apache.http.impl.conn.SystemDefaultRoutePlanner
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sparenaproxy")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@KtorExperimentalAPI
fun main() {
    val env = Environment()
    val vaultSecrets = VaultSecrets()
    DefaultExports.initialize()
    val applicationState = ApplicationState()

    val vaultCredentialService = VaultCredentialService()
    val database = Database(env, vaultCredentialService)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        config()
        engine {
            customizeClient {
                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
            }
        }
    }
    val httpClientWithProxy = HttpClient(Apache, proxyConfig)
    val httpClient = HttpClient(Apache, config)

    // val oidcClient = StsOidcClient(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
    // val syfoSyketilfelleClient = SyfoSyketilfelleClient(env.syketilfelleEndpointURL, oidcClient, httpClient)
    val accessTokenClient = AccessTokenClient(env.aadAccessTokenUrl, vaultSecrets.clientId, vaultSecrets.clientSecret, httpClientWithProxy)
    val spokelseClient = SpokelseClient(env.spokelseEndpointURL, accessTokenClient, "", httpClient)

    val kafkaClients = KafkaClients(env, vaultSecrets)
    val utbetaltEventConsumer = UtbetaltEventConsumer(kafkaClients.kafkaUtbetaltEventConsumer)
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(database)
    val vedtakService = VedtakService(applicationState, utbetaltEventConsumer, spokelseClient, lagreUtbetaltEventOgPlanlagtMeldingService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    createListener(applicationState) {
        vedtakService.start()
    }

    // Del 1:
    // lytt på topic
    // finn sykmeldingsid fra nytt api (kommer)
    // slå opp i syfosyketilfelle for å finne startdato for riktig sykeforløp
    // lagre utbetaltevent med startdato, samt planlagt varsel

    // Del 2:
    // cronjobb som sender varsel til arena
    // hvis sendes er passert og ikke sendt/avbrutt: slå opp i utbetaltevents, sjekk om aktivt vedtak på samme fnr?
    // hvis ja: send melding og sett til sendt. Hvis nei: Sett til avbrutt.

    // Del 3:
    // ikke sett til sendt før kvittering er mottatt.
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk galt", ex.message)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
