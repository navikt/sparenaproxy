package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val log: Logger = LoggerFactory.getLogger("no.nav.syfo.sparenaproxy")

val objectMapper: ObjectMapper = ObjectMapper().apply {
    registerKotlinModule()
    registerModule(JavaTimeModule())
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
}

@DelicateCoroutinesApi
fun main() {
    val env = Environment()
    val vaultSecrets = Serviceuser()
    DefaultExports.initialize()
    val applicationState = ApplicationState()
//
//    val database = Database(env)
//
//    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
//        install(ContentNegotiation) {
//            jackson {
//                registerKotlinModule()
//                registerModule(JavaTimeModule())
//                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
//                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
//            }
//        }
//        HttpResponseValidator {
//            handleResponseExceptionWithRequest { exception, _ ->
//                when (exception) {
//                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
//                }
//            }
//        }
//    }
//
//    val proxyConfig: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
//        config()
//        engine {
//            customizeClient {
//                setRoutePlanner(SystemDefaultRoutePlanner(ProxySelector.getDefault()))
//            }
//        }
//    }
//    val httpClientWithProxy = HttpClient(Apache, proxyConfig)
//    val httpClient = HttpClient(Apache, config)
//
//    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, clientId = env.clientIdV2, clientSecret = env.clientSecretV2, httpClient = httpClientWithProxy)
//    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
//        syketilfelleEndpointURL = env.syketilfelleEndpointURL,
//        accessTokenClientV2 = accessTokenClientV2,
//        resourceId = env.syketilfelleScope,
//        httpClient = httpClient,
//        cluster = env.cluster
//    )
//    val smregisterClient = SmregisterClient(env.smregisterEndpointURL, accessTokenClientV2, env.smregisterScope, httpClient)
//    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)
//
//    val connection = connectionFactory(env).createConnection(vaultSecrets.serviceuserUsername, vaultSecrets.serviceuserPassword)
//
//    connection.start()
//    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
//    val kvitteringConsumer = session.consumerForQueue(env.kvitteringQueueName)
//    val backoutProducer = session.producerForQueue(env.backoutQueueName)
//    val arenaProducer = session.producerForQueue(env.arenaQueueName)
//    val arenaMqProducer = ArenaMqProducer(session, arenaProducer)
//    val arenaMeldingService = ArenaMeldingService(arenaMqProducer)
//
//    val kafkaClients = KafkaClients(env, vaultSecrets)
//    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(database)
//    val maksdatoService = MaksdatoService(arenaMqProducer, pdlPersonService)
//    val utbetaltEventService = UtbetaltEventService(syfoSyketilfelleClient, lagreUtbetaltEventOgPlanlagtMeldingService, maksdatoService)
//
//    val aktiverMeldingService = AktiverMeldingService(database, smregisterClient, arenaMeldingService, pdlPersonService, syfoSyketilfelleClient)
//
//    val kvitteringListener = KvitteringListener(applicationState, kvitteringConsumer, backoutProducer, KvitteringService(database))
//
//    val mottattSykmeldingService = MottattSykmeldingService(database, syfoSyketilfelleClient, arenaMeldingService)
//
//    val personhendelserConsumer = PersonhendelserConsumer(kafkaClients.personhendelserKafkaConsumer)
//    val dodshendelserService = DodshendelserService(applicationState, personhendelserConsumer, database)
//
//    val commonAivenKafkaService = CommonAivenKafkaService(applicationState, kafkaClients.aivenKafkaConsumer, env, utbetaltEventService, mottattSykmeldingService, aktiverMeldingService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

//
//    createListener(applicationState) {
//        dodshendelserService.start()
//    }
//    createListener(applicationState) {
//        kvitteringListener.start()
//    }
//    createListener(applicationState) {
//        commonAivenKafkaService.start()
//    }

    applicationServer.start()
}

@DelicateCoroutinesApi
fun createListener(applicationState: ApplicationState, action: suspend CoroutineScope.() -> Unit): Job =
    GlobalScope.launch {
        try {
            action()
        } catch (ex: Exception) {
            log.error("Noe gikk galt: ${ex.message}", ex)
        } finally {
            applicationState.alive = false
            applicationState.ready = false
        }
    }
