package no.nav.syfo.kafka

import kotlinx.coroutines.delay
import no.nav.syfo.Environment
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.lagrevedtak.UtbetaltEventService
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Duration

class CommonAivenKafkaService(
    private val applicationState: ApplicationState,
    private val kafkaConsumer: KafkaConsumer<String, String>,
    private val env: Environment,
    private val utbetaltEventService: UtbetaltEventService
) {
    suspend fun start() {
        kafkaConsumer.subscribe(
            listOf(
                env.utbetaltEventAivenTopic
            )
        )

        log.info("Starter Aiven Kafka consumer")
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(Duration.ofMillis(0))
            records.forEach {
                if (it.value() != null) {
                    when (it.topic()) {
                        env.utbetaltEventAivenTopic -> {
                            utbetaltEventService.mottaUtbetaltEvent(it.value())
                        }
                        else -> throw IllegalStateException("Har mottatt melding på ukjent topic: ${it.topic()}")
                    }
                }
            }
            delay(1)
        }
    }
}
