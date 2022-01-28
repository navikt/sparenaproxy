package no.nav.syfo.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.aktivermelding.AktiverMeldingService
import no.nav.syfo.aktivermelding.MottattSykmeldingService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.lagrevedtak.UtbetaltEventService
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class CommonAivenKafkaService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val env: Environment,
    private val utbetaltEventService: UtbetaltEventService,
    private val mottattSykmeldingService: MottattSykmeldingService,
    private val aktiverMeldingService: AktiverMeldingService
) {
    suspend fun start() {
        kafkaConsumer.subscribe(
            listOf(
                env.utbetaltEventAivenTopic,
                env.okSykmeldingTopic,
                env.manuellSykmeldingTopic,
                env.aktiverMeldingAivenTopic
            )
        )

        log.info("Starter Aiven Kafka consumer")
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                if (it.value() != null) {
                    when (it.topic()) {
                        env.utbetaltEventAivenTopic -> utbetaltEventService.mottaUtbetaltEvent(it.value())
                        env.okSykmeldingTopic -> mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.manuellSykmeldingTopic -> mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.aktiverMeldingAivenTopic -> aktiverMeldingService.mottaAktiverMelding(it.value()).also { log.info("Mottatt aktivermelding på topic ${env.aktiverMeldingAivenTopic}") }
                        else -> throw IllegalStateException("Har mottatt melding på ukjent topic: ${it.topic()}")
                    }
                }
            }
            delay(1)
        }
    }
}
