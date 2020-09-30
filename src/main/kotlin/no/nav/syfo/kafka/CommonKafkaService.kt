package no.nav.syfo.kafka

import io.ktor.util.KtorExperimentalAPI
import java.time.Duration
import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.aktivermelding.AktiverMeldingService
import no.nav.syfo.aktivermelding.MottattSykmeldingService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.dodshendelser.DodshendelserService
import no.nav.syfo.lagrevedtak.VedtakService
import org.apache.kafka.clients.consumer.KafkaConsumer

@KtorExperimentalAPI
class CommonKafkaService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val env: Environment,
    private val vedtakService: VedtakService,
    private val mottattSykmeldingService: MottattSykmeldingService,
    private val aktiverMeldingService: AktiverMeldingService,
    private val dodshendelserService: DodshendelserService
) {
    suspend fun start() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                if (it.value() != null) {
                    when (it.topic()) {
                        env.utbetaltEventTopic -> vedtakService.handleVedtak(it.value())
                        env.aktiverMeldingTopic -> aktiverMeldingService.handleAktiverMelding(it.value())
                        env.sykmeldingAutomatiskBehandlingTopic -> mottattSykmeldingService.handleMottattSykmelding(it.value())
                        env.sykmeldingManuellBehandlingTopic -> mottattSykmeldingService.handleMottattSykmelding(it.value())
                        env.pdlTopic -> dodshendelserService.handlePersonhendelse(it.value())
                        else -> throw IllegalStateException("Har mottatt melding på ukjent topic: ${it.topic()}")
                    }
                }
            }
            delay(1)
        }
    }
}
