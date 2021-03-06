package no.nav.syfo.aktivermelding.client

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
import io.ktor.util.KtorExperimentalAPI
import io.mockk.coEvery
import io.mockk.mockk
import java.net.ServerSocket
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SmregisterClientTest : Spek({
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

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEachTest {
        coEvery { accessTokenClientMock.getAccessTokenV2(any()) } returns "token"
    }

    describe("Test av SmRegisterClient - 100% sykmeldt") {
        it("Er 100% sykmeldt hvis sykmeldt og ikke gradert") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr", UUID.randomUUID())
            }

            erSykmeldt shouldEqual true
        }
        it("Er ikke 100% sykmeldt hvis sykmeldt, men gradert") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr-gradert", UUID.randomUUID())
            }

            erSykmeldt shouldEqual false
        }
        it("Er ikke 100% sykmeldt hvis ikke lenger sykmeldt") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.er100ProsentSykmeldt("fnr-ikkesyk", UUID.randomUUID())
            }

            erSykmeldt shouldEqual false
        }
    }

    describe("Test av SmRegisterClient - sykmeldt uavhengig av grad") {
        it("Er sykmeldt hvis sykmeldt og ikke gradert") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.erSykmeldt("fnr", UUID.randomUUID())
            }

            erSykmeldt shouldEqual true
        }
        it("Er sykmeldt hvis sykmeldt, men gradert") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.erSykmeldt("fnr-gradert", UUID.randomUUID())
            }

            erSykmeldt shouldEqual true
        }
        it("Er ikke sykmeldt hvis ikke lenger sykmeldt") {
            var erSykmeldt: Boolean? = null
            runBlocking {
                erSykmeldt = smregisterClient.erSykmeldt("fnr-ikkesyk", UUID.randomUUID())
            }

            erSykmeldt shouldEqual false
        }
    }

    describe("Test av SmRegisterClient - sykmeldt til og med-dato") {
        it("Henter tom-dato hvis bruker er sykmeldt") {
            var sykmeldtTom: LocalDate? = null
            runBlocking {
                sykmeldtTom = smregisterClient.erSykmeldtTilOgMed("fnr", UUID.randomUUID())
            }

            sykmeldtTom shouldEqual tom
        }
        it("Tom-dato er null hvis ikke lenger sykmeldt") {
            var sykmeldtTom: LocalDate? = null
            runBlocking {
                sykmeldtTom = smregisterClient.erSykmeldtTilOgMed("fnr-ikkesyk", UUID.randomUUID())
            }

            sykmeldtTom shouldEqual null
        }
        it("Feiler hvis bruker er sykmeldt, men tom-dato mangler") {
            assertFailsWith<IllegalStateException> {
                runBlocking {
                    smregisterClient.erSykmeldtTilOgMed("fnr-sykmeldtutentom", UUID.randomUUID())
                }
            }
        }
    }
})
