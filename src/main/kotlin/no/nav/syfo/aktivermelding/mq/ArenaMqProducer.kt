package no.nav.syfo.aktivermelding.mq

import java.util.UUID
import javax.jms.MessageProducer
import javax.jms.Session
import javax.jms.TextMessage

class ArenaMqProducer(
    private val session: Session,
    private val messageProducer: MessageProducer
) {

    fun sendTilArena(melding: String, meldingId: UUID) {
        messageProducer.send(session.createTextMessage().apply(createMessage(melding, meldingId)))
    }

    private fun createMessage(melding: String, meldingId: UUID): TextMessage.() -> Unit {
        return {
            text = melding
            jmsCorrelationID = meldingId.toString()
        }
    }
}
