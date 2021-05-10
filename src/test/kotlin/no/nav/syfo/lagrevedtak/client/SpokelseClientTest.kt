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
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.SpokelseAccessTokenClient
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object SpokelseClientTest : Spek({
    val hendelseId1 = UUID.randomUUID()
    val hendelseId2 = UUID.randomUUID()
    val sykmeldingUUID = UUID.randomUUID()
    val soknadUUD = UUID.randomUUID()
    val accessTokenClientMock = mockk<SpokelseAccessTokenClient>()
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
            jackson {}
        }
        routing {
            get("/dokumenter") {
                call.respond(listOf(Hendelse(dokumentId = sykmeldingUUID, hendelseId = hendelseId1, type = "Sykmelding"), Hendelse(dokumentId = soknadUUD, hendelseId = hendelseId2, type = "Søknad")))
            }
        }
    }.start()

    val spokelseClient = SpokelseClient(mockHttpServerUrl, accessTokenClientMock, "resourceId", httpClient)

    afterGroup {
        mockServer.stop(TimeUnit.SECONDS.toMillis(1), TimeUnit.SECONDS.toMillis(1))
    }

    beforeEachTest {
        coEvery { accessTokenClientMock.hentAccessToken(any()) } returns "token"
    }

    describe("Test av SpokelseClient") {
        it("Henter sykmeldingId fra spokelse") {
            var sykmeldingId: UUID? = null
            runBlocking {
                sykmeldingId = spokelseClient.finnSykmeldingId(listOf(hendelseId1, hendelseId2).toSet(), UUID.randomUUID())
            }

            sykmeldingId shouldEqual sykmeldingUUID
        }
    }
})
