package no.nav.syfo.application.metrics

import io.prometheus.client.Counter
import io.prometheus.client.Histogram

const val METRICS_NS = "sparenaproxy"

val HTTP_HISTOGRAM: Histogram = Histogram.Builder()
    .labelNames("path")
    .name("requests_duration_seconds")
    .help("http requests durations for incoming requests in seconds")
    .register()

val MOTTATT_VEDTAK: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("mottatt_vedtak_counter")
    .help("Antall mottatte vedtak/utbetalingsevents")
    .register()

val KUN_LAGRET_VEDTAK: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("lagret_vedtak_counter")
    .help("Antall lagrede vedtak u/planlagt melding")
    .register()

val OPPRETTET_PLANLAGT_MELDING: Counter = Counter.Builder()
    .namespace(METRICS_NS)
    .name("planlagt_melding_counter")
    .labelNames("type")
    .help("Antall opprettede planlagte meldinger med forskjellig type")
    .register()
