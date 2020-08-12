package no.nav.syfo.dodshendelser

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object DodshendelserServiceTest : Spek({
    describe("Håndtering av dødsfall") {
        it("Avbryter planlagte meldinger for avdød bruker") {
            // rører ikke sendte/avbrutte meldinger
        }
        it("Feiler ikke hvis personidenter ikke finnes") {
        }
    }

    describe("Fanger opp riktig type hendelser") {
        it("Opprettet dødshendelse fanges opp") {
        }
        it("Korrigert dødshendelse fanges opp") {
        }
        it("Opprettet fødselshendelse ignoreres") {
        }
    }
})
