package no.nav.syfo.pdl.service

import io.ktor.util.KtorExperimentalAPI
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.sts.OidcToken
import no.nav.syfo.client.sts.StsOidcClient
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Doedsfall
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.ResponseData
import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

@KtorExperimentalAPI
object PdlPersonServiceTest : Spek({
    val pdlClient = mockk<PdlClient>()
    val stsOidcClient = mockk<StsOidcClient>()
    val pdlService = PdlPersonService(pdlClient, stsOidcClient)

    beforeEachTest {
        clearAllMocks()
        coEvery { stsOidcClient.oidcToken() } returns OidcToken("Token", "JWT", 1L)
    }

    describe("PersonPdlService - erPersonDod") {
        it("Returnerer true hvis dødsfall-liste ikke er tom") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(HentPerson(listOf(Doedsfall("2020-08-11")))),
                errors = null
            )

            runBlocking {
                pdlService.isAlive("123", UUID.randomUUID()) shouldEqual false
            }
        }
        it("Returnerer false hvis dødsfall-liste er tom") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(HentPerson(emptyList())),
                errors = null
            )

            runBlocking {
                pdlService.isAlive("123", UUID.randomUUID()) shouldEqual true
            }
        }
        it("Kaster feil hvis person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null), errors = null)

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    pdlService.isAlive("123", UUID.randomUUID())
                }
            }
        }
    }
})
