package no.nav.syfo.lagrevedtak

import io.kotest.core.spec.style.FunSpec
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import no.nav.syfo.lagrevedtak.db.lagreUtbetaltEventOgPlanlagtMelding
import no.nav.syfo.model.AKTIVITETSKRAV_8_UKER_TYPE
import no.nav.syfo.model.BREV_4_UKER_TYPE
import no.nav.syfo.model.PlanlagtMeldingDbModel
import no.nav.syfo.model.STANS_TYPE
import no.nav.syfo.testutil.TestDB
import no.nav.syfo.testutil.dropData
import no.nav.syfo.testutil.hentPlanlagtMelding
import no.nav.syfo.testutil.hentUtbetaltEvent
import no.nav.syfo.testutil.lagUtbetaltEvent
import no.nav.syfo.testutil.lagrePlanlagtMelding
import no.nav.syfo.testutil.opprettPlanlagtMelding
import org.amshove.kluent.shouldBeEqualTo

class LagreUtbetaltEventOgPlanlagtMeldingServiceTest :
    FunSpec({
        val testDb = TestDB.database
        val lagreUtbetaltEventOgPlanlagtMeldingService =
            LagreUtbetaltEventOgPlanlagtMeldingService(testDb)

        val utbetaltEventId = UUID.fromString("58ac4866-5944-48a1-99fa-86d6f9f3103c")

        afterTest { testDb.connection.dropData() }

        context("Test av lagring av vedtaksinfo og planlagte meldinger") {
            val startdato = LocalDate.of(2020, 6, 1)
            val tom = LocalDate.of(2020, 7, 15)
            test("Lagrer vedtak, stansmelding og planlagt melding 4, 8 for nytt tilfelle") {
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr", tom)

                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 3
                utbetaltEventFraDbListe.size shouldBeEqualTo 1
                val planlagtMelding4uker =
                    planlagtMeldingFraDbListe.find { it.type == BREV_4_UKER_TYPE }
                val planlagtMelding8uker =
                    planlagtMeldingFraDbListe.find { it.type == AKTIVITETSKRAV_8_UKER_TYPE }
                val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
                val utbetaltEventFraDb = utbetaltEventFraDbListe.first()

                planlagtMelding4uker?.fnr shouldBeEqualTo "fnr"
                planlagtMelding4uker?.startdato shouldBeEqualTo startdato
                planlagtMelding4uker?.type shouldBeEqualTo BREV_4_UKER_TYPE
                planlagtMelding4uker?.sendes shouldBeEqualTo
                    startdato
                        .plusWeeks(4)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                planlagtMelding4uker?.sendt shouldBeEqualTo null
                planlagtMelding4uker?.avbrutt shouldBeEqualTo null

                planlagtMelding8uker?.fnr shouldBeEqualTo "fnr"
                planlagtMelding8uker?.startdato shouldBeEqualTo startdato
                planlagtMelding8uker?.type shouldBeEqualTo AKTIVITETSKRAV_8_UKER_TYPE
                planlagtMelding8uker?.sendes shouldBeEqualTo
                    startdato
                        .plusWeeks(8)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                planlagtMelding8uker?.sendt shouldBeEqualTo null
                planlagtMelding8uker?.avbrutt shouldBeEqualTo null
                planlagtStansmelding?.fnr shouldBeEqualTo "fnr"
                planlagtStansmelding?.startdato shouldBeEqualTo startdato
                planlagtStansmelding?.type shouldBeEqualTo STANS_TYPE
                planlagtStansmelding?.sendes shouldBeEqualTo
                    tom.plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                planlagtStansmelding?.sendt shouldBeEqualTo null
                planlagtStansmelding?.avbrutt shouldBeEqualTo null

                utbetaltEventFraDb shouldBeEqualTo utbetaltEvent
            }
            test(
                "Lagrer kun vedtak og oppdaterer stansmelding, hvis planlagte meldinger finnes for syketilfellet fra før"
            ) {
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr", tom)
                val nesteUtbetaltEvent =
                    lagUtbetaltEvent(UUID.randomUUID(), startdato, "fnr", tom.plusWeeks(1))

                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent
                )
                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    nesteUtbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 3
                utbetaltEventFraDbListe.size shouldBeEqualTo 2
                val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
                planlagtStansmelding?.sendes shouldBeEqualTo
                    tom.plusWeeks(1)
                        .plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
            }
            test(
                "Oppdaterer ikke stansmelding hvis nytt utsendingstidspunkt er tidligere enn det forrige"
            ) {
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr", tom)
                val nesteUtbetaltEvent =
                    lagUtbetaltEvent(UUID.randomUUID(), startdato, "fnr", tom.minusWeeks(1))

                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent
                )
                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    nesteUtbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 3
                val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
                planlagtStansmelding?.sendes shouldBeEqualTo
                    tom.plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
            }
            test("Gjenåpner stansmelding hvis stansmelding for samme sykefravær var avbrutt") {
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr", tom)
                val nesteUtbetaltEvent =
                    lagUtbetaltEvent(UUID.randomUUID(), startdato, "fnr", tom.plusWeeks(1))
                testDb.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent,
                    listOf(
                        PlanlagtMeldingDbModel(
                            id = UUID.randomUUID(),
                            fnr = "fnr",
                            startdato = startdato,
                            type = STANS_TYPE,
                            opprettet =
                                tom.minusWeeks(10)
                                    .atStartOfDay()
                                    .atZone(ZoneId.systemDefault())
                                    .withZoneSameInstant(ZoneOffset.UTC)
                                    .toOffsetDateTime(),
                            sendes =
                                tom.minusWeeks(8)
                                    .atStartOfDay()
                                    .atZone(ZoneId.systemDefault())
                                    .withZoneSameInstant(ZoneOffset.UTC)
                                    .toOffsetDateTime(),
                            avbrutt =
                                tom.minusWeeks(8)
                                    .atStartOfDay()
                                    .atZone(ZoneId.systemDefault())
                                    .withZoneSameInstant(ZoneOffset.UTC)
                                    .toOffsetDateTime(),
                            sendt = null
                        )
                    )
                )
                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    nesteUtbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 1
                val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
                planlagtStansmelding?.sendes shouldBeEqualTo
                    tom.plusWeeks(1)
                        .plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
                planlagtStansmelding?.avbrutt shouldBeEqualTo null
            }
            test("Oppretter stansmelding hvis det ikke finnes fra før") {
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr", tom)
                testDb.connection.lagrePlanlagtMelding(
                    opprettPlanlagtMelding(
                        id = UUID.randomUUID(),
                        fnr = "fnr",
                        startdato = startdato
                    )
                )

                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 2
                val planlagtStansmelding = planlagtMeldingFraDbListe.find { it.type == STANS_TYPE }
                planlagtStansmelding?.sendes shouldBeEqualTo
                    tom.plusDays(17)
                        .atStartOfDay()
                        .atZone(ZoneId.systemDefault())
                        .withZoneSameInstant(ZoneOffset.UTC)
                        .toOffsetDateTime()
            }
            test(
                "Lagrer vedtak og melding hvis planlagt melding finnes for tidligere syketilfelle for samme bruker"
            ) {
                val nesteStartdato = startdato.plusMonths(1)
                val utbetaltEvent = lagUtbetaltEvent(utbetaltEventId, startdato, "fnr")
                val nesteUtbetaltEvent = lagUtbetaltEvent(UUID.randomUUID(), nesteStartdato, "fnr")

                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    utbetaltEvent
                )
                lagreUtbetaltEventOgPlanlagtMeldingService.lagreUtbetaltEventOgPlanlagtMelding(
                    nesteUtbetaltEvent
                )

                val planlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", startdato)
                val nestePlanlagtMeldingFraDbListe =
                    testDb.connection.hentPlanlagtMelding("fnr", nesteStartdato)
                val utbetaltEventFraDbListe = testDb.connection.hentUtbetaltEvent("fnr", startdato)
                val nesteUtbetaltEventFraDbListe =
                    testDb.connection.hentUtbetaltEvent("fnr", nesteStartdato)
                planlagtMeldingFraDbListe.size shouldBeEqualTo 3
                nestePlanlagtMeldingFraDbListe.size shouldBeEqualTo 3
                utbetaltEventFraDbListe.size shouldBeEqualTo 1
                nesteUtbetaltEventFraDbListe.size shouldBeEqualTo 1
            }
        }
    })
