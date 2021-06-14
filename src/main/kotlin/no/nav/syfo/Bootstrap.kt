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
import javax.jms.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.aktivermelding.AktiverMeldingService
import no.nav.syfo.aktivermelding.ArenaMeldingService
import no.nav.syfo.aktivermelding.KvitteringService
import no.nav.syfo.aktivermelding.MottattSykmeldingService
import no.nav.syfo.aktivermelding.client.SmregisterClient
import no.nav.syfo.aktivermelding.mq.ArenaMqProducer
import no.nav.syfo.aktivermelding.mq.KvitteringListener
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.VaultCredentialService
import no.nav.syfo.application.vault.RenewVaultService
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.client.sts.StsOidcClient
import no.nav.syfo.dodshendelser.DodshendelserService
import no.nav.syfo.dodshendelser.kafka.PersonhendelserConsumer
import no.nav.syfo.kafka.CommonAivenKafkaService
import no.nav.syfo.kafka.CommonKafkaService
import no.nav.syfo.kafka.KafkaClients
import no.nav.syfo.lagrevedtak.LagreUtbetaltEventOgPlanlagtMeldingService
import no.nav.syfo.lagrevedtak.UtbetaltEventService
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
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

    val oidcClient = StsOidcClient(username = vaultSecrets.serviceuserUsername, password = vaultSecrets.serviceuserPassword, stsUrl = env.stsUrl)
    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        env.syketilfelleEndpointURL,
        oidcClient,
        httpClient,
        env.cluster
    )
    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, clientId = env.clientIdV2, clientSecret = env.clientSecretV2, httpClient = httpClientWithProxy)
    val spokelseClient = SpokelseClient(env.spokelseEndpointURL, accessTokenClientV2, env.spokelseScope, httpClient)
    val smregisterClient = SmregisterClient(env.smregisterEndpointURL, accessTokenClientV2, env.smregisterScope, httpClient)
    val pdlPersonService = PdlFactory.getPdlService(env, oidcClient, httpClient)

    val connection = connectionFactory(env).createConnection(vaultSecrets.mqUsername, vaultSecrets.mqPassword)

    connection.start()
    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
    val kvitteringConsumer = session.consumerForQueue(env.kvitteringQueueName)
    val backoutProducer = session.producerForQueue(env.backoutQueueName)
    val arenaProducer = session.producerForQueue(env.arenaQueueName)
    val arenaMqProducer = ArenaMqProducer(session, arenaProducer)
    val arenaMeldingService = ArenaMeldingService(arenaMqProducer)

    val kafkaClients = KafkaClients(env, vaultSecrets)
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(database)
    val maksdatoService = MaksdatoService(arenaMqProducer, pdlPersonService)
    val utbetaltEventService = UtbetaltEventService(spokelseClient, syfoSyketilfelleClient, lagreUtbetaltEventOgPlanlagtMeldingService, maksdatoService)

    val aktiverMeldingService = AktiverMeldingService(database, smregisterClient, arenaMeldingService, pdlPersonService)

    val kvitteringListener = KvitteringListener(applicationState, kvitteringConsumer, backoutProducer, KvitteringService(database))

    val mottattSykmeldingService = MottattSykmeldingService(database, syfoSyketilfelleClient, arenaMeldingService)

    val personhendelserConsumer = PersonhendelserConsumer(kafkaClients.personhendelserKafkaConsumer)
    val dodshendelserService = DodshendelserService(applicationState, personhendelserConsumer, database)

    val commonKafkaService = CommonKafkaService(applicationState, kafkaClients.kafkaConsumer, env, utbetaltEventService, mottattSykmeldingService, aktiverMeldingService)
    val commonAivenKafkaService = CommonAivenKafkaService(applicationState, kafkaClients.aivenKafkaConsumer, env, utbetaltEventService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    createListener(applicationState) {
        commonKafkaService.start()
    }
    createListener(applicationState) {
        dodshendelserService.start()
    }
    createListener(applicationState) {
        kvitteringListener.start()
    }
    createListener(applicationState) {
        commonAivenKafkaService.start()
    }
}

fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk galt: {}", ex.message)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
