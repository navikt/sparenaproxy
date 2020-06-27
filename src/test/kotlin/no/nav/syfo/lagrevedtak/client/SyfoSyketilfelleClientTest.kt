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
                call.respond(SyketilfelleRespons(listOf(
                    Sykeforloep(oppfolgingsdato1, listOf(SimpleSykmelding(UUID.randomUUID().toString(), oppfolgingsdato1, oppfolgingsdato1.plusWeeks(3)))),
                    Sykeforloep(oppfolgingsdato2, listOf(SimpleSykmelding(sykmeldingUUID.toString(), oppfolgingsdato2, oppfolgingsdato2.plusWeeks(4)))),
                    Sykeforloep(oppfolgingsdato3, listOf(SimpleSykmelding(UUID.randomUUID().toString(), oppfolgingsdato3, oppfolgingsdato3.plusWeeks(8))))
                )))
            }
            get("/sparenaproxy/$aktorId2/sykeforloep") {
                call.respond(SyketilfelleRespons(listOf(
                    Sykeforloep(oppfolgingsdato1, listOf(SimpleSykmelding(UUID.randomUUID().toString(), oppfolgingsdato1, oppfolgingsdato1.plusWeeks(3)))),
                    Sykeforloep(oppfolgingsdato3, listOf(SimpleSykmelding(UUID.randomUUID().toString(), oppfolgingsdato3, oppfolgingsdato3.plusWeeks(8))))
                )))
            }
        }
    }.start()

    val syfoSyketilfelleClient = SyfoSyketilfelleClient(mockHttpServerUrl, stsOidcClient, httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEachTest {
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("token", "type", 100)
    }

    describe("Test av SyfoSyketilfelleClient") {
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
    }
})
