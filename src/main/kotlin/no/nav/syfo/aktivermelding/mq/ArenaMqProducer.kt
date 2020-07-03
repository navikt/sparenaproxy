package no.nav.syfo.aktivermelding.mq

import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

class ArenaMqProducer(
    private val session: Session,
    private val messageProducer: MessageProducer
) {

    fun sendTilArena(melding: String) {
        messageProducer.send(session.createTextMessage().apply(createMessage(melding)))
    }

    private fun createMessage(melding: String): TextMessage.() -> Unit {
        return {
            text = melding
        }
    }
}
