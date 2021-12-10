package no.nav.syfo.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.aktivermelding.AktiverMeldingService
import no.nav.syfo.aktivermelding.MottattSykmeldingService
import no.nav.syfo.application.ApplicationState
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class CommonKafkaService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val env: Environment,
    private val mottattSykmeldingService: MottattSykmeldingService,
    private val aktiverMeldingService: AktiverMeldingService
) {
    suspend fun start() {
        kafkaConsumer.subscribe(
            listOf(
                env.aktiverMeldingTopic,
                env.sykmeldingAutomatiskBehandlingTopic,
                env.sykmeldingManuellBehandlingTopic
            )
        )

        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                if (it.value() != null) {
                    when (it.topic()) {
                        env.aktiverMeldingTopic -> aktiverMeldingService.mottaAktiverMelding(it.value())
                        env.sykmeldingAutomatiskBehandlingTopic -> mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.sykmeldingManuellBehandlingTopic -> mottattSykmeldingService.mottaNySykmelding(it.value())
                        else -> throw IllegalStateException("Har mottatt melding på ukjent topic: ${it.topic()}")
                    }
                }
            }
            delay(1)
        }
    }
}
