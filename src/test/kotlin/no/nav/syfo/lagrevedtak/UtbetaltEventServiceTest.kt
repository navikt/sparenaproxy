package no.nav.syfo.lagrevedtak

import io.ktor.util.KtorExperimentalAPI
import io.mockk.mockk
import java.time.LocalDate
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.lagrevedtak.client.SpokelseClient
import no.nav.syfo.lagrevedtak.maksdato.MaksdatoService
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object UtbetaltEventServiceTest : Spek({
    val spokelseClient = mockk<SpokelseClient>()
    val syfoSyketilfelleClient = mockk<SyfoSyketilfelleClient>()
    val lagreUtbetaltEventOgPlanlagtMeldingService = mockk<LagreUtbetaltEventOgPlanlagtMeldingService>()
    val maksdatoService = mockk<MaksdatoService>()

    val utbetaltEventService = UtbetaltEventService(spokelseClient, syfoSyketilfelleClient, lagreUtbetaltEventOgPlanlagtMeldingService, maksdatoService)

    describe("Test av logikk for om det skal sendes maksdato") {
        val fnr = "15060188888"
        it("Skal sende maksdatomelding hvis sykmeldt i 4 uker og 1 dag") {
            val startdato = LocalDate.now().minusWeeks(4).minusDays(1)

            utbetaltEventService.skalSendeMaksdatomelding(fnr, startdato) shouldEqual true
        }
        it("Skal ikke sende maksdatomelding hvis sykmeldt i 3 uker og 6 dager") {
            val startdato = LocalDate.now().minusWeeks(3).minusDays(6)

            utbetaltEventService.skalSendeMaksdatomelding(fnr, startdato) shouldEqual false
        }
    }
})
