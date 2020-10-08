package no.nav.syfo

import org.amshove.kluent.shouldEqual
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FiltreringTest : Spek({

    describe("Test av aldersfiltrering med etter 1990-filter") {
        it("Bruker født i 1990 gir true med etter 1990-filter") {
            trefferAldersfilter("15069088888", Filter.ETTER1990) shouldEqual true
        }
        it("Bruker født i 2001 gir true med etter 1990-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1990) shouldEqual true
        }
        it("Bruker født i 1989 gir false med etter 1990-filter") {
            trefferAldersfilter("15068988888", Filter.ETTER1990) shouldEqual false
        }
    }

    describe("Test av aldersfiltrering med etter 1980-filter") {
        it("Bruker født i 1985 gir true med etter 1980-filter") {
            trefferAldersfilter("15068588888", Filter.ETTER1980) shouldEqual true
        }
        it("Bruker født i 2001 gir true med etter 1980-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1980) shouldEqual true
        }
        it("Bruker født i 1979 gir false med etter 1990-filter") {
            trefferAldersfilter("15067988888", Filter.ETTER1980) shouldEqual false
        }
    }
})
