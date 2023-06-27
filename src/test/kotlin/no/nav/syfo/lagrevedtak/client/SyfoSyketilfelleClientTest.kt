package no.nav.syfo.lagrevedtak.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.client.SimpleSykmelding
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.client.Sykeforloep
import org.amshove.kluent.shouldBeEqualTo

class SyfoSyketilfelleClientTest :
    FunSpec({
        val sykmeldingUUID = UUID.randomUUID()
        val oppfolgingsdato1 = LocalDate.of(2019, 9, 30)
        val oppfolgingsdato2 = LocalDate.of(2020, 5, 30)
        val oppfolgingsdato3 = LocalDate.of(2018, 10, 15)

        val fnr1 = "123456"
        val fnr2 = "654321"
        val fnr3 = "111222"

        val accessTokenClientMock = mockk<AccessTokenClientV2>()
        val httpClient =
            HttpClient(Apache) {
                install(ContentNegotiation) {
                    jackson {
                        registerKotlinModule()
                        registerModule(JavaTimeModule())
                        configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    }
                }
            }

        val mockHttpServerPort = ServerSocket(0).use { it.localPort }
        val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
        val mockServer =
            embeddedServer(Netty, mockHttpServerPort) {
                    install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
                        jackson {
                            registerKotlinModule()
                            registerModule(JavaTimeModule())
                            configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                        }
                    }
                    routing {
                        get("/api/v1/sykeforloep") {
                            when (call.request.headers["fnr"]) {
                                fnr1 ->
                                    call.respond(
                                        listOf(
                                            Sykeforloep(
                                                oppfolgingsdato1,
                                                listOf(
                                                    SimpleSykmelding(
                                                        UUID.randomUUID().toString(),
                                                        oppfolgingsdato1,
                                                        oppfolgingsdato1.plusWeeks(3)
                                                    )
                                                )
                                            ),
                                            Sykeforloep(
                                                oppfolgingsdato2,
                                                listOf(
                                                    SimpleSykmelding(
                                                        sykmeldingUUID.toString(),
                                                        oppfolgingsdato2,
                                                        oppfolgingsdato2.plusWeeks(4)
                                                    )
                                                )
                                            ),
                                            Sykeforloep(
                                                oppfolgingsdato3,
                                                listOf(
                                                    SimpleSykmelding(
                                                        UUID.randomUUID().toString(),
                                                        oppfolgingsdato3,
                                                        oppfolgingsdato3.plusWeeks(8)
                                                    )
                                                )
                                            )
                                        )
                                    )
                                fnr2 ->
                                    call.respond(
                                        listOf(
                                            Sykeforloep(
                                                oppfolgingsdato1,
                                                listOf(
                                                    SimpleSykmelding(
                                                        UUID.randomUUID().toString(),
                                                        oppfolgingsdato1,
                                                        oppfolgingsdato1.plusWeeks(3)
                                                    )
                                                )
                                            ),
                                            Sykeforloep(
                                                oppfolgingsdato3,
                                                listOf(
                                                    SimpleSykmelding(
                                                        UUID.randomUUID().toString(),
                                                        oppfolgingsdato3,
                                                        oppfolgingsdato3.plusWeeks(8)
                                                    )
                                                )
                                            )
                                        )
                                    )
                                fnr3 -> call.respond(emptyList<Sykeforloep>())
                            }
                        }
                    }
                }
                .start()

        val syfoSyketilfelleClient =
            SyfoSyketilfelleClient(
                mockHttpServerUrl,
                accessTokenClientMock,
                "resource",
                httpClient,
                "prod-gcp"
            )

        afterSpec { mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1)) }

        beforeTest { coEvery { accessTokenClientMock.getAccessTokenV2(any()) } returns "token" }

        context("Test av SyfoSyketilfelleClient - finnStartDato") {
            test("Henter riktig startdato fra syfosyketilfelle") {
                val startDato =
                    syfoSyketilfelleClient.finnStartdato(
                        fnr1,
                        sykmeldingUUID.toString(),
                        UUID.randomUUID()
                    )

                startDato shouldBeEqualTo oppfolgingsdato2
            }
            test("Kaster feil hvis sykmelding ikke er knyttet til syketilfelle") {
                assertFailsWith<RuntimeException> {
                    runBlocking {
                        syfoSyketilfelleClient.finnStartdato(
                            fnr2,
                            sykmeldingUUID.toString(),
                            UUID.randomUUID()
                        )
                    }
                }
            }
            test(
                "Returnerer dato hvis sykmelding ikke er knyttet til syketilfelle og vi kjører i dev-gcp"
            ) {
                val syfoSyketilfelleClientDev =
                    SyfoSyketilfelleClient(
                        mockHttpServerUrl,
                        accessTokenClientMock,
                        "resource",
                        httpClient,
                        "dev-gcp"
                    )
                val startDato =
                    syfoSyketilfelleClientDev.finnStartdato(
                        fnr2,
                        sykmeldingUUID.toString(),
                        UUID.randomUUID()
                    )

                startDato shouldBeEqualTo LocalDate.now().minusMonths(1)
            }
        }

        context("Test av SyfoSyketilfelleClient - finnStartdatoGittFomOgTom") {
            val sykeforloep =
                listOf(
                    Sykeforloep(
                        oppfolgingsdato1,
                        listOf(
                            SimpleSykmelding(
                                UUID.randomUUID().toString(),
                                oppfolgingsdato1,
                                oppfolgingsdato1.plusWeeks(3)
                            ),
                            SimpleSykmelding(
                                UUID.randomUUID().toString(),
                                oppfolgingsdato1.plusWeeks(3),
                                oppfolgingsdato1.plusWeeks(7)
                            ),
                            SimpleSykmelding(
                                UUID.randomUUID().toString(),
                                oppfolgingsdato1.plusWeeks(8),
                                oppfolgingsdato1.plusWeeks(19)
                            )
                        )
                    ),
                    Sykeforloep(
                        oppfolgingsdato2,
                        listOf(
                            SimpleSykmelding(
                                UUID.randomUUID().toString(),
                                oppfolgingsdato2,
                                oppfolgingsdato2.plusWeeks(4)
                            )
                        )
                    ),
                    Sykeforloep(
                        oppfolgingsdato3,
                        listOf(
                            SimpleSykmelding(
                                UUID.randomUUID().toString(),
                                oppfolgingsdato3,
                                oppfolgingsdato3.plusWeeks(8)
                            )
                        )
                    )
                )
            test("Finner riktig startdato når fom og tom er en sykmeldingsperiode") {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato2,
                        tom = oppfolgingsdato2.plusWeeks(4),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato2
            }
            test("Finner riktig startdato når fom og tom er en del av en sykmeldingsperiode") {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato3.plusWeeks(1),
                        tom = oppfolgingsdato3.plusWeeks(3),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato3
            }
            test(
                "Finner riktig startdato når fom er første utbetalingsdag og tom er en del av en senere sykmeldingsperiode"
            ) {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato1,
                        tom = oppfolgingsdato1.plusWeeks(18),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato1
            }
            test(
                "Finner riktig startdato når fom er før startdato og tom er en del av en senere sykmeldingsperiode"
            ) {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato1.minusWeeks(1),
                        tom = oppfolgingsdato1.plusWeeks(18),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato1
            }
            test("Finner riktig startdato når fom er siste dag i siste sykmeldingsperiode") {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato3.plusWeeks(8),
                        tom = oppfolgingsdato3.plusWeeks(10),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato3
            }
            test(
                "Finner riktig startdato når fom og tom overlapper med opphold i sykmeldingsperioder"
            ) {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato1.plusWeeks(7).plusDays(1),
                        tom = oppfolgingsdato1.plusWeeks(7).plusDays(5),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo oppfolgingsdato1
            }
            test(
                "Finner ikke startdato når fom er dagen etter siste dag i siste sykmeldingsperiode"
            ) {
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato1.plusWeeks(19).plusDays(1),
                        tom = oppfolgingsdato1.plusWeeks(20),
                        sykeforloep = sykeforloep
                    )

                startdato shouldBeEqualTo null
            }
            test("Bruker nyeste startdato hvis to syketilfeller overlapper med hverandre") {
                val oppfolgingsdato4 = LocalDate.of(2020, 3, 5)
                val oppfolgingsdato5 = oppfolgingsdato4.plusWeeks(1)
                val sykeforloepMedOverlapp =
                    listOf(
                        Sykeforloep(
                            oppfolgingsdato4,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato4,
                                    oppfolgingsdato4.plusWeeks(3)
                                )
                            )
                        ),
                        Sykeforloep(
                            oppfolgingsdato5,
                            listOf(
                                SimpleSykmelding(
                                    UUID.randomUUID().toString(),
                                    oppfolgingsdato5,
                                    oppfolgingsdato5.plusWeeks(4)
                                )
                            )
                        )
                    )
                val startdato =
                    syfoSyketilfelleClient.finnStartdatoGittFomOgTom(
                        fom = oppfolgingsdato4.plusWeeks(2),
                        tom = oppfolgingsdato4.plusWeeks(3),
                        sykeforloep = sykeforloepMedOverlapp
                    )

                startdato shouldBeEqualTo oppfolgingsdato5
            }
        }

        context("Test av SyfoSyketilfelleClient - harSykeforlopMedNyereStartdato") {
            test("Returnerer true hvis det finnes syketilfeller med nyere startdato") {
                val startDato = LocalDate.of(2020, 5, 1)

                syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(
                    fnr1,
                    startDato,
                    UUID.randomUUID()
                ) shouldBeEqualTo true
            }
            test("Returnerer false hvis det ikke finnes syketilfeller med nyere startdato") {
                val startDato = LocalDate.of(2020, 5, 30)

                syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(
                    fnr1,
                    startDato,
                    UUID.randomUUID()
                ) shouldBeEqualTo false
            }
            test("Kaster feil hvis det ikke finnes noen syketilfeller") {
                assertFailsWith<RuntimeException> {
                    runBlocking {
                        syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(
                            fnr3,
                            LocalDate.now(),
                            UUID.randomUUID()
                        )
                    }
                }
            }
            test(
                "Returnerer false hvis det ikke finnes noen syketilfeller og vi kjører i dev-gcp"
            ) {
                val syfoSyketilfelleClientDev =
                    SyfoSyketilfelleClient(
                        mockHttpServerUrl,
                        accessTokenClientMock,
                        "resource",
                        httpClient,
                        "dev-gcp"
                    )

                syfoSyketilfelleClientDev.harSykeforlopMedNyereStartdato(
                    fnr3,
                    LocalDate.now(),
                    UUID.randomUUID()
                ) shouldBeEqualTo false
            }
        }
    })
