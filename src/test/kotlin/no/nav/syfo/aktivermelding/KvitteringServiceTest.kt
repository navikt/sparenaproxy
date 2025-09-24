package no.nav.syfo.aktivermelding

import io.kotest.core.spec.style.FunSpec
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding

class KvitteringServiceTest :
    FunSpec({
        val kvitteringsmelding =
            "K278M890Kvittering Arena002270307202010070612345678910J                                                                                                                                                                            "
        val kvitteringsmeldingMedFeil =
            "K278M890Kvittering Arena002270307202010070612345678910NXXXXXXXXFeilmelding                                                                                                                                                         "

        val testDb = TestDB.database
        val kvitteringService = KvitteringService(testDb)

        afterTest { testDb.connection.dropData() }

        context("Test av behandleKvittering") {
            test("Prøver å resende hvis kvitteringstatus ikke er ok og melding finnes i database") {
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = UUID.randomUUID(),
                        sendt = OffsetDateTime.now(ZoneOffset.UTC),
                        jmsCorrelationId = "correlationId"
                    )
                )

                kvitteringService.behandleKvittering(kvitteringsmeldingMedFeil, "correlationId")
            }
            test("Feiler ikke hvis kvitteringsstatus er ok") {
                kvitteringService.behandleKvittering(kvitteringsmelding, "correlationId")
            }
        }
    })
