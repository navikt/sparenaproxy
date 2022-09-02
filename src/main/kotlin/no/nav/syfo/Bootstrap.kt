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
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.network.sockets.SocketTimeoutException
import io.ktor.serialization.jackson.jackson
import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
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
import no.nav.syfo.application.exeption.ServiceUnavailableException
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.dodshendelser.DodshendelserService
import no.nav.syfo.dodshendelser.kafka.PersonhendelserConsumer
import no.nav.syfo.kafka.CommonAivenKafkaService
import no.nav.syfo.kafka.KafkaClients
import no.nav.syfo.lagrevedtak.LagreUtbetaltEventOgPlanlagtMeldingService
import no.nav.syfo.lagrevedtak.UtbetaltEventService
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import no.nav.syfo.mq.MqTlsUtils
import no.nav.syfo.mq.connectionFactory
import no.nav.syfo.mq.consumerForQueue
import no.nav.syfo.mq.producerForQueue
import no.nav.syfo.pdl.PdlFactory
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.jms.Session

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
    val serviceuser = Serviceuser()
    MqTlsUtils.getMqTlsConfig().forEach { key, value -> System.setProperty(key as String, value as String) }
    DefaultExports.initialize()
    val applicationState = ApplicationState()

    val database = Database(env)

    val config: HttpClientConfig<ApacheEngineConfig>.() -> Unit = {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        HttpResponseValidator {
            handleResponseExceptionWithRequest { exception, _ ->
                when (exception) {
                    is SocketTimeoutException -> throw ServiceUnavailableException(exception.message)
                }
            }
        }
    }

    val httpClient = HttpClient(Apache, config)

    val accessTokenClientV2 = AccessTokenClientV2(env.aadAccessTokenV2Url, clientId = env.clientIdV2, clientSecret = env.clientSecretV2, httpClient = httpClient)
    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        syketilfelleEndpointURL = env.syketilfelleEndpointURL,
        accessTokenClientV2 = accessTokenClientV2,
        resourceId = env.syketilfelleScope,
        httpClient = httpClient,
        cluster = env.cluster
    )
    val smregisterClient = SmregisterClient(env.smregisterEndpointURL, accessTokenClientV2, env.smregisterScope, httpClient)
    val pdlPersonService = PdlFactory.getPdlService(env, httpClient, accessTokenClientV2, env.pdlScope)

    val connection = connectionFactory(env).createConnection(serviceuser.serviceuserUsername, serviceuser.serviceuserPassword)

    connection.start()
    val session = connection.createSession(false, Session.CLIENT_ACKNOWLEDGE)
    val kvitteringConsumer = session.consumerForQueue(env.kvitteringQueueName)
    val backoutProducer = session.producerForQueue(env.backoutQueueName)
    val arenaProducer = session.producerForQueue(env.arenaQueueName)
    val arenaMqProducer = ArenaMqProducer(session, arenaProducer)
    val arenaMeldingService = ArenaMeldingService(arenaMqProducer)

    val kafkaClients = KafkaClients(env, serviceuser)
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(database)
    val maksdatoService = MaksdatoService(arenaMqProducer, pdlPersonService)
    val utbetaltEventService = UtbetaltEventService(syfoSyketilfelleClient, lagreUtbetaltEventOgPlanlagtMeldingService, maksdatoService)

    val aktiverMeldingService = AktiverMeldingService(database, smregisterClient, arenaMeldingService, pdlPersonService, syfoSyketilfelleClient)

    val kvitteringListener = KvitteringListener(applicationState, kvitteringConsumer, backoutProducer, KvitteringService(database))

    val mottattSykmeldingService = MottattSykmeldingService(database, syfoSyketilfelleClient, arenaMeldingService)

    val personhendelserConsumer = PersonhendelserConsumer(kafkaClients.personhendelserKafkaConsumer)
    val dodshendelserService = DodshendelserService(applicationState, personhendelserConsumer, database)

    val commonAivenKafkaService = CommonAivenKafkaService(applicationState, kafkaClients.aivenKafkaConsumer, env, utbetaltEventService, mottattSykmeldingService, aktiverMeldingService)

    val applicationEngine = createApplicationEngine(
        env,
        applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)

    createListener(applicationState) {
        dodshendelserService.start()
    }
    createListener(applicationState) {
        kvitteringListener.start()
    }
    createListener(applicationState) {
        commonAivenKafkaService.start()
    }

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
