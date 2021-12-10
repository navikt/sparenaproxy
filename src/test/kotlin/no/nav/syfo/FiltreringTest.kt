package no.nav.syfo

import org.amshove.kluent.shouldBeEqualTo
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe

object FiltreringTest : Spek({

    describe("Test av aldersfiltrering med etter 1995-filter") {
        it("Bruker født i 1995 gir true med etter 1995-filter") {
            trefferAldersfilter("15069588888", Filter.ETTER1995) shouldBeEqualTo true
        }
        it("Bruker født i 2001 gir true med etter 1995-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1995) shouldBeEqualTo true
        }
        it("Bruker født i 1994 gir false med etter 1995-filter") {
            trefferAldersfilter("15069488888", Filter.ETTER1995) shouldBeEqualTo false
        }
    }

    describe("Test av aldersfiltrering med etter 1990-filter") {
        it("Bruker født i 1990 gir true med etter 1990-filter") {
            trefferAldersfilter("15069088888", Filter.ETTER1990) shouldBeEqualTo true
        }
        it("Bruker født i 2001 gir true med etter 1990-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1990) shouldBeEqualTo true
        }
        it("Bruker født i 1989 gir false med etter 1990-filter") {
            trefferAldersfilter("15068988888", Filter.ETTER1990) shouldBeEqualTo false
        }
    }

    describe("Test av aldersfiltrering med etter 1980-filter") {
        it("Bruker født i 1985 gir true med etter 1980-filter") {
            trefferAldersfilter("15068588888", Filter.ETTER1980) shouldBeEqualTo true
        }
        it("Bruker født i 2001 gir true med etter 1980-filter") {
            trefferAldersfilter("15060188888", Filter.ETTER1980) shouldBeEqualTo true
        }
        it("Bruker født i 1979 gir false med etter 1990-filter") {
            trefferAldersfilter("15067988888", Filter.ETTER1980) shouldBeEqualTo false
        }
    }
})
