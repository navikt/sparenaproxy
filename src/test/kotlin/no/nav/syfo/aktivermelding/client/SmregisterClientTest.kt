package no.nav.syfo.aktivermelding.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.kotest.core.spec.style.FunSpec
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.features.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.accept
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import org.amshove.kluent.shouldBeEqualTo
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith

class SmregisterClientTest : FunSpec({
    val fom = LocalDate.of(2020, 3, 15)
    val tom = LocalDate.of(2020, 4, 12)
    val accessTokenClientMock = mockk<AccessTokenClientV2>()
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
            accept(ContentType.Application.Json) {
                post("/api/v2/sykmelding/sykmeldtStatus") {
                    when (call.receive<StatusRequest>().fnr) {
                        "fnr" -> call.respond(HttpStatusCode.OK, SykmeldtStatus(erSykmeldt = true, gradert = false, fom = fom, tom = tom))
                        "fnr-ikkesyk" -> call.respond(HttpStatusCode.OK, SykmeldtStatus(erSykmeldt = false))
                        "fnr-gradert" -> call.respond(HttpStatusCode.OK, SykmeldtStatus(erSykmeldt = true, gradert = true, fom = fom, tom = tom))
                        "fnr-sykmeldtutentom" -> call.respond(HttpStatusCode.OK, SykmeldtStatus(erSykmeldt = true, gradert = true, fom = fom, tom = null))
                    }
                }
            }
        }
    }.start()

    val smregisterClient = SmregisterClient(mockHttpServerUrl, accessTokenClientMock, "resourceId", httpClient)

    afterSpec {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeTest {
        coEvery { accessTokenClientMock.getAccessTokenV2(any()) } returns "token"
    }

    context("Test av SmRegisterClient - 100% sykmeldt") {
        test("Er 100% sykmeldt hvis sykmeldt og ikke gradert") {
            val erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo true
        }
        test("Er ikke 100% sykmeldt hvis sykmeldt, men gradert") {
            val erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr-gradert", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo false
        }
        test("Er ikke 100% sykmeldt hvis ikke lenger sykmeldt") {
            val erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr-ikkesyk", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo false
        }
    }

    context("Test av SmRegisterClient - sykmeldt uavhengig av grad") {
        test("Er sykmeldt hvis sykmeldt og ikke gradert") {
            val erSykmeldt = smregisterClient.erSykmeldt("fnr", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo true
        }
        test("Er sykmeldt hvis sykmeldt, men gradert") {
            val erSykmeldt = smregisterClient.erSykmeldt("fnr-gradert", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo true
        }
        test("Er ikke sykmeldt hvis ikke lenger sykmeldt") {
            val erSykmeldt = smregisterClient.erSykmeldt("fnr-ikkesyk", UUID.randomUUID())

            erSykmeldt shouldBeEqualTo false
        }
    }

    context("Test av SmRegisterClient - sykmeldt til og med-dato") {
        test("Henter tom-dato hvis bruker er sykmeldt") {
            val sykmeldtTom = smregisterClient.erSykmeldtTilOgMed("fnr", UUID.randomUUID())

            sykmeldtTom shouldBeEqualTo tom
        }
        test("Tom-dato er null hvis ikke lenger sykmeldt") {
            val sykmeldtTom = smregisterClient.erSykmeldtTilOgMed("fnr-ikkesyk", UUID.randomUUID())

            sykmeldtTom shouldBeEqualTo null
        }
        test("Feiler hvis bruker er sykmeldt, men tom-dato mangler") {
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    smregisterClient.erSykmeldtTilOgMed("fnr-sykmeldtutentom", UUID.randomUUID())
                }
            }
        }
    }
})
