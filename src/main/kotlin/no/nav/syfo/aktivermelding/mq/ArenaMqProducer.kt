package no.nav.syfo.aktivermelding.mq

import jakarta.jms.MessageProducer
import jakarta.jms.Session
import jakarta.jms.TextMessage

class ArenaMqProducer(private val session: Session, private val messageProducer: MessageProducer) {

    fun sendTilArena(melding: String): String {
        val message = session.createTextMessage().apply(createMessage(melding))
        messageProducer.send(message)
        return message.jmsMessageID
    }

    private fun createMessage(melding: String): TextMessage.() -> Unit {
        return { text = melding }
    }
}
