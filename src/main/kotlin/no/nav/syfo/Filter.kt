package no.nav.syfo

fun trefferAldersfilter(fnr: String, filter: Filter): Boolean {
    return filter.regex.toRegex().matches(fnr)
}

enum class Filter(val regex: String) {
    ETTER1990("\\d{4}((0|9)[0-9])\\d{5}"),
    ETTER1980("\\d{4}((0|[8-9])[0-9])\\d{5}")
}
