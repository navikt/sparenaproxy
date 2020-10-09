package no.nav.syfo.aktivermelding

import kotlin.test.assertFailsWith
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object KvitteringServiceTest : Spek({
    val kvitteringsmelding = "K278M890Kvittering Arena002270307202010070612345678910J                                                                                                                                                                            "
    val kvitteringsmeldingMedFeil = "K278M890Kvittering Arena002270307202010070612345678910NXXXXXXXXFeilmelding                                                                                                                                                         "

    val kvitteringService = KvitteringService()

    describe("Test av behandleKvittering") {
        it("Feiler hvis kvitteringstatus ikke er ok") {
            assertFailsWith<RuntimeException> {
                kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil)
            }
        }

        it("Feiler ikke hvis kvitteringsstatus er ok") {
            kvitteringService.behandleKvittering(kvitteringsmelding)
        }
    }
})
