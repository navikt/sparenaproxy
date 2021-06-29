package no.nav.syfo.lagrevedtak.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.jackson.jackson
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.SimpleSykmelding
import no.nav.syfo.client.SyfoSyketilfelleClient
import no.nav.syfo.client.Sykeforloep
import no.nav.syfo.client.sts.OidcToken
import no.nav.syfo.client.sts.StsOidcClient
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SyfoSyketilfelleClientTest : Spek({
    val sykmeldingUUID = UUID.randomUUID()
    val oppfolgingsdato1 = LocalDate.of(2019, 9, 30)
    val oppfolgingsdato2 = LocalDate.of(2020, 1, 30)
    val oppfolgingsdato3 = LocalDate.of(2018, 10, 15)

    val aktorId1 = "123456"
    val aktorId2 = "654321"
    val aktorId3 = "111222"

    val stsOidcClient = mockk<StsOidcClient>()
    val httpClient = HttpClient(Apache) {
        install(JsonFeature) {
            serializer = JacksonSerializer {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
    }

    val mockHttpServerPort = ServerSocket(0).use { it.localPort }
    val mockHttpServerUrl = "http://localhost:$mockHttpServerPort"
    val mockServer = embeddedServer(Netty, mockHttpServerPort) {
        install(ContentNegotiation) {
            jackson {
                registerKotlinModule()
                registerModule(JavaTimeModule())
                configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }
        routing {
            get("/sparenaproxy/$aktorId1/sykeforloep") {
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
            }
            get("/sparenaproxy/$aktorId2/sykeforloep") {
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
            }
            get("/sparenaproxy/$aktorId3/sykeforloep") {
                call.respond(emptyList<Sykeforloep>())
            }
        }
    }.start()

    val syfoSyketilfelleClient = SyfoSyketilfelleClient(
        mockHttpServerUrl,
        stsOidcClient,
        httpClient,
        "prod-fss"
    )

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEachTest {
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("token", "type", 100)
    }

    describe("Test av SyfoSyketilfelleClient - finnStartDato") {
        it("Henter riktig startdato fra syfosyketilfelle") {
            var startDato: LocalDate? = null
            runBlocking {
                startDato = syfoSyketilfelleClient.finnStartdato(aktorId1, sykmeldingUUID.toString(), UUID.randomUUID())
            }

            startDato shouldEqual oppfolgingsdato2
        }
        it("Kaster feil hvis sykmelding ikke er knyttet til syketilfelle") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfoSyketilfelleClient.finnStartdato(aktorId2, sykmeldingUUID.toString(), UUID.randomUUID())
                }
            }
        }
        it("Returnerer dato hvis sykmelding ikke er knyttet til syketilfelle og vi kjører i dev-fss") {
            val syfoSyketilfelleClientDev = SyfoSyketilfelleClient(
                mockHttpServerUrl,
                stsOidcClient,
                httpClient,
                "dev-fss"
            )
            var startDato: LocalDate? = null
            runBlocking {
                startDato = syfoSyketilfelleClientDev.finnStartdato(aktorId2, sykmeldingUUID.toString(), UUID.randomUUID())
            }

            startDato shouldEqual LocalDate.now().minusMonths(1)
        }
    }

    describe("Test av SyfoSyketilfelleClient - harSykeforlopMedNyereStartdato") {
        it("Returnerer true hvis det finnes syketilfeller med nyere startdato") {
            val startDato = LocalDate.of(2020, 1, 1)
            runBlocking {
                syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(aktorId1, startDato, UUID.randomUUID()) shouldEqual true
            }
        }
        it("Returnerer false hvis det ikke finnes syketilfeller med nyere startdato") {
            val startDato = LocalDate.of(2020, 1, 30)
            runBlocking {
                syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(aktorId1, startDato, UUID.randomUUID()) shouldEqual false
            }
        }
        it("Kaster feil hvis det ikke finnes noen syketilfeller") {
            assertFailsWith<RuntimeException> {
                runBlocking {
                    syfoSyketilfelleClient.harSykeforlopMedNyereStartdato(aktorId3, LocalDate.now(), UUID.randomUUID())
                }
            }
        }
        it("Returnerer false hvis det ikke finnes noen syketilfeller og vi kjører i dev-fss") {
            val syfoSyketilfelleClientDev = SyfoSyketilfelleClient(
                mockHttpServerUrl,
                stsOidcClient,
                httpClient,
                "dev-fss"
            )
            runBlocking {
                syfoSyketilfelleClientDev.harSykeforlopMedNyereStartdato(aktorId3, LocalDate.now(), UUID.randomUUID()) shouldEqual false
            }
        }
    }
})
