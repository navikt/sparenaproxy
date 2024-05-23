package no.nav.syfo.kafka

import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import no.nav.syfo.Environment
import no.nav.syfo.aktivermelding.AktiverMeldingService
import no.nav.syfo.aktivermelding.MottattSykmeldingService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.lagrevedtak.UtbetaltEventService
import no.nav.syfo.log
import org.apache.kafka.clients.consumer.KafkaConsumer

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
                env.utbetalingTopic,
                env.okSykmeldingTopic,
                env.manuellSykmeldingTopic,
                env.aktiverMeldingAivenTopic
            )
        )

        log.info("Starter Aiven Kafka consumer")
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(10.seconds.toJavaDuration())
            if (records.isEmpty) {
                delay(1.seconds)
            }
            records.forEach {
                if (it.value() != null) {
                    when (it.topic()) {
                        env.utbetalingTopic -> utbetaltEventService.mottaUtbetaltEvent(it.value())
                        env.okSykmeldingTopic ->
                            mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.manuellSykmeldingTopic ->
                            mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.aktiverMeldingAivenTopic ->
                            aktiverMeldingService.mottaAktiverMelding(it.value())
                        else ->
                            throw IllegalStateException(
                                "Har mottatt melding på ukjent topic: ${it.topic()}"
                            )
                    }
                }
            }
            yield()
        }
    }
}
