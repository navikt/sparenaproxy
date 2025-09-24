package no.nav.syfo.kafka

import io.opentelemetry.api.GlobalOpenTelemetry.getTracerProvider
import io.opentelemetry.api.trace.Tracer
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

        log.info("Starter Aiven Kafka consumer")
        while (applicationState.ready) {
            try {
                kafkaConsumer.subscribe(
                    listOf(
                        env.utbetalingTopic,
                        env.okSykmeldingTopic,
                        env.manuellSykmeldingTopic,
                        env.aktiverMeldingAivenTopic
                    )
                )
                consumeMessages()
            } catch (ex: Exception) {
                log.error(
                    "Error prosessing message, unsubscribing and waiting 60s: ${ex.message}",
                    ex
                )
                kafkaConsumer.unsubscribe()
                delay(60.seconds)
            }
        }
    }

    private suspend fun consumeMessages() {
        while (applicationState.ready) {
            val records = kafkaConsumer.poll(10.seconds.toJavaDuration())
            if (records.isEmpty) {
                delay(1.seconds)
            }

            val tracer: Tracer =
                getTracerProvider().get("${CommonAivenKafkaService::class}:kafka-consumer-root")

            records.forEach {
                val span = tracer.spanBuilder("kafka-consumer-root.records.message").startSpan()
                if (it.value() != null) {
                    when (it.topic()) {
                        env.utbetalingTopic -> utbetaltEventService.mottaUtbetaltEvent(it.value())
                        env.okSykmeldingTopic ->
                            mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.manuellSykmeldingTopic ->
                            mottattSykmeldingService.mottaNySykmelding(it.value())
                        env.aktiverMeldingAivenTopic ->
                            aktiverMeldingService.mottaAktiverMelding(it.value())
                        else -> {
                            span.addEvent("Ukjent topic")
                            span.end()
                            throw IllegalStateException(
                                "Har mottatt melding på ukjent topic: ${it.topic()}",
                            )
                        }
                    }
                }
                span.end()
            }
            yield()
        }
    }
}
