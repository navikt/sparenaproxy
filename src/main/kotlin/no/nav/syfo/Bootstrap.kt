package no.nav.syfo

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.util.KtorExperimentalAPI
import io.prometheus.client.hotspot.DefaultExports
import no.nav.syfo.application.ApplicationServer
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.createApplicationEngine
import no.nav.syfo.application.db.Database
import no.nav.syfo.application.db.VaultCredentialService
import no.nav.syfo.application.vault.RenewVaultService
import no.nav.syfo.lagrevedtak.LagreUtbetaltEventOgPlanlagtMeldingService
import no.nav.syfo.lagrevedtak.VedtakService
import no.nav.syfo.lagrevedtak.kafka.UtbetaltEventConsumer
import no.nav.syfo.util.KafkaClients
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

    val applicationEngine = createApplicationEngine(
            env,
            applicationState
    )
    val applicationServer = ApplicationServer(applicationEngine, applicationState)
    applicationServer.start()
    applicationState.ready = true

    RenewVaultService(vaultCredentialService, applicationState).startRenewTasks()

    val kafkaClients = KafkaClients(env, vaultSecrets)
    val utbetaltEventConsumer = UtbetaltEventConsumer(kafkaClients.kafkaUtbetaltEventConsumer)
    val lagreUtbetaltEventOgPlanlagtMeldingService = LagreUtbetaltEventOgPlanlagtMeldingService(database)
    val vedtakService = VedtakService(applicationState, utbetaltEventConsumer, lagreUtbetaltEventOgPlanlagtMeldingService)

    vedtakService.start()

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
