package no.nav.syfo.pdl.service

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.mockkClass
import kotlinx.coroutines.runBlocking
import no.nav.syfo.client.AccessTokenClientV2
import no.nav.syfo.pdl.client.PdlClient
import no.nav.syfo.pdl.client.model.Doedsfall
import no.nav.syfo.pdl.client.model.GetPersonResponse
import no.nav.syfo.pdl.client.model.HentPerson
import no.nav.syfo.pdl.client.model.ResponseData
import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import java.util.UUID
import kotlin.test.assertFailsWith

object PdlPersonServiceTest : Spek({
    val pdlClient = mockk<PdlClient>()
    val accessTokenClient = mockkClass(AccessTokenClientV2::class)
    val pdlService = PdlPersonService(pdlClient, accessTokenClient, "pdlscope")

    beforeEachTest {
        clearAllMocks()
    }

    describe("PersonPdlService - erPersonDod") {
        it("Returnerer true hvis dødsfall-liste ikke er tom") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(HentPerson(listOf(Doedsfall("2020-08-11")))),
                errors = null
            )
            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            runBlocking {
                pdlService.isAlive("123", UUID.randomUUID()) shouldBeEqualTo false
            }
        }
        it("Returnerer false hvis dødsfall-liste er tom") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(
                ResponseData(HentPerson(emptyList())),
                errors = null
            )
            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            runBlocking {
                pdlService.isAlive("123", UUID.randomUUID()) shouldBeEqualTo true
            }
        }
        it("Kaster feil hvis person ikke finnes i PDL") {
            coEvery { pdlClient.getPerson(any(), any()) } returns GetPersonResponse(ResponseData(null), errors = null)
            coEvery { accessTokenClient.getAccessTokenV2(any()) } returns "token"

            assertFailsWith<IllegalStateException> {
                runBlocking {
                    pdlService.isAlive("123", UUID.randomUUID())
                }
            }
        }
    }
})
