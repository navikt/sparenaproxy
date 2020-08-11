package no.nav.syfo.aktivermelding.mq

import javax.jms.MessageConsumer
import javax.jms.MessageProducer
import javax.jms.TextMessage
import kotlinx.coroutines.delay
import no.nav.syfo.aktivermelding.KvitteringService
import no.nav.syfo.application.ApplicationState
import no.nav.syfo.application.metrics.KVITTERING_FEILET
import no.nav.syfo.log

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
                log.info("Mottatt kvittering")
                kvitteringService.behandleKvittering(inputMessageText)
            } catch (e: Exception) {
                log.error("Noe gikk galt ved håndtering av kvitteringsmelding, sender melding til backout", e.message)
                backoutProducer.send(message)
                KVITTERING_FEILET.inc()
            } finally {
                message.acknowledge()
            }
        }
    }
}
