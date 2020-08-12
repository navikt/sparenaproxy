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

val MOTTATT_AKTIVERMELDING: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("mottatt_aktivermelding_counter")
    .help("Antall mottatte aktivermeldinger")
    .register()

val AVBRUTT_MELDING: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("avbrutt_melding_counter")
    .help("Antall avbrutte meldinger")
    .register()

val SENDT_MELDING: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("sendt_melding_counter")
    .help("Antall sendte meldinger")
    .register()

val IKKE_FUNNET_MELDING: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("ikke_funnet_melding_counter")
    .help("Antall meldinger som er sendt/avbrutt fra før")
    .register()

val KVITTERING_FEILET: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("kvittering_feilet_counter")
    .help("Antall kvitteringer som har feilet")
    .register()

val KVITTERING_SENDT: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("kvittering_sendt_counter")
    .help("Planlagt melding som det er mottatt ok kvittering for")
    .register()

val SENDT_AVBRUTT_MELDING: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("sendt_avbrutt_counter")
    .help("Avbrutt melding som sendes ved økning av grad")
    .register()

val AVBRUTT_MELDING_DODSFALL: Counter = Counter.build()
    .namespace(METRICS_NS)
    .name("avbrutt_melding_dodsfall_counter")
    .help("Antall avbrutte meldinger pga dødsfall")
    .register()
