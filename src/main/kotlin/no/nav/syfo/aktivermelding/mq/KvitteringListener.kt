package no.nav.syfo.aktivermelding.mq

import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.KvitteringService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.KVITTERING_FEILET
import no.nav.syfo.log
import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage

class KvitteringListener(
    private val applicationState: ApplicationState,
    private val kvitteringConsumer: MessageConsumer,
    private val backoutProducer: MessageProducer,
    private val kvitteringService: KvitteringService
) {
    suspend fun start() {
        while (applicationState.ready) {
            val message = kvitteringConsumer.receiveNoWait()
            if (message == null) {
                delay(100)
                continue
            }
            try {
                val inputMessageText = when (message) {
                    is TextMessage -> message.text
                    else -> throw RuntimeException("Innkommende melding må være bytes eller tekst")
                }
                val correlationId = message.jmsCorrelationID
                log.info("Mottatt kvittering med correlationId $correlationId")
                kvitteringService.behandleKvittering(inputMessageText, correlationId)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved håndtering av kvitteringsmelding, sender melding til backout: ${e.message}")
                backoutProducer.send(message)
                KVITTERING_FEILET.inc()
            } finally {
                message.acknowledge()
            }
        }
    }
}
