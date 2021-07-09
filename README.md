# Sparenaproxy

## Funksjonell beskrivelse

Sparenaproxy sin hensikt er å sørge for at Arena har oppdatert informasjon om hvor langt en sykmeldt har kommet i sykefraværet slik at NAV kan gi 
riktig oppfølging. Tidligere ble dette håndtert av Infotrygd, og Sparenaproxy viderefører kun noen av meldingstypene som Infotrygd tidligere 
sendte. Sparenaproxy kan sende disse meldingene: 
* 4-ukersmelding
* 8-ukersmelding (aktivitetskravmelding)
* 39-ukersmelding
* maksdatomelding (start/gjenåpning av syketilfelle i Arena, og justering av maksdato)
* stansmelding (stans av syketilfelle i Arena)

Sparenaproxy lytter på nye sykmeldinger, og på utbetalingsmeldinger fra Speil. Ved mottak av en utbetalingsmelding lagrer sparenaproxy utbetalingsmeldingene i sin helhet. 
Den sender også umiddelbart en maksdatomelding, og hvis det ikke finnes fra før oppretter den planlagte meldinger for alle de andre meldingstypene som skal sendes 
på bestemte tidpsunkter i løpet av sykefraværet. Sykefraværet identifiseres unikt gjennom kombinasjonen av den sykmeldtes fødselsnummer og startdato for syketilfellet. 

Meldingene sendes på samme format som Infotrygd brukte (COBOL copybook) til Arena via MQ. Arena svarer med en kvittering som kan være ok eller ikke 
ok. Hvis kvitteringen ikke er ok og meldingen ikke var en maksdatomelding (dvs, det var en planlagt melding), vil vi forsøke å resende den. Maksdatomeldinger 
persisteres ikke, og resendes heller ikke. I løpet av et sykeforløp vil det komme flere maksdatomeldinger, og vi anser dem som fire and forget. Maksdatomelding sendes 
kun for sykefravær som har vart i minst 20 dager. 

### Planlagte meldinger
Alt bortsett fra maksdatomeldingene er "planlagte meldinger". De opprettes ved mottak av første utbetalingsmelding for et sykefravær med en gitt startdato. 
Planlagte meldinger opprettes med en dato for når de skal sendes, og de har også datofelter for når de ble sendt, eventuelt avbrutt. 
Cronjobben [sparenajob](https://github.com/navikt/sparenajob) vil på faste tidspunkt gå gjennom databasen på jakt etter meldinger som har passert 
utsendingstidspunkt, men som ikke er sendt eller avbrutt. De meldingene cronjobben finner skrives til en kafkatopic og fanges opp av `AktiverMeldingService`. 
Om meldingen sendes, avbrytes eller evt utsettes kommer an på meldingstypen. 

#### 4-ukersmelding
4-ukersmeldingen opprettes med utsendelsedato fire uker frem i tid fra startdato for syketilfellet. Meldingen sendes hvis bruker fortsatt er sykmeldt (uavhengig av grad) 
ved utsendingstidspunktet, hvis ikke settes den til avbrutt. 

#### 8-ukersmelding
8-ukersmeldingen opprettes med utsendelsedato åtte uker frem i tid fra startdato for syketilfellet. Meldingen sendes i utgangspunktet hvis bruker er 100% sykmeldt ved 
utsendingstidspunktet og avbrytes hvis bruker ikke lenger er sykmeldt, men hvis det har blitt sendt stansmelding for syketilfellet så må vi også sjekke at det ikke har kommet noe nyere 
syketilfelle (nyere startdato) før vi sender meldingen til Arena. Hvis bruker fortsatt er sykmeldt, men det er et nyere syketilfelle så avbrytes meldingen. 

Hvis vi har avbrutt en 8-ukersmelding og det kommer en ny sykmelding som ikke er gradert for det samme syketilfellet så sendes 8-ukersmeldingen likevel. 

#### 39-ukersmelding
39-ukersmeldingen opprettes med utsendelsedato 39 uker frem i tid fra startdato for syketilfellet. 39-ukersmeldingen fungerer veldig likt som 8-ukersmeldingen: Meldingen sendes 
i utgangspunktet hvis bruker er sykmeldt (uavhengig av grad) ved utsendingstidspunktet og avbrytes hvis bruker ikke lenger er sykmeldt, men hvis det har blitt sendt stansmelding 
for syketilfellet så må vi også sjekke at det ikke har kommet noe nyere syketilfelle (nyere startdato) før vi sender meldingen til Arena. Hvis bruker fortsatt er sykmeldt, men 
det er et nyere syketilfelle så avbrytes meldingen. 

Hvis vi har avbrutt en 39-ukersmelding og det kommer en ny sykmelding for det samme syketilfellet så sendes 39-ukersmeldingen likevel. 

At nettopp 39-ukersmeldingen sendes til rett tid er kanskje det aller viktigste fordi den sørger for at den sykmeldte får beskjed om at det snart er slutt på sykepengene og at de evt. må søke AAP. 

#### Stansmelding
Stansmelding opprettes med utsendelsesdato som er 17 dager etter tom-dato i utbetalingsmeldingen. Hvis stansmelding for dette syketilfellet finnes fra før settes nytt utsendingstidspunkt 
til 17 dager etter tom-datoen i den mottatte utbetalingsmeldingen. Hvis det kommer en ny sykmelding for samme syketilfelle utsetter vi utsendelsestidspunkt for stansmeldingen til 
17 dager etter seneste tom-dato fra sykmeldingsperioden. 

Ved utsendingstidspunkt for stansmelding sjekker vi om bruker fortsatt er sykmeldt, og evt hva som er siste tom-dato for den sykmeldingsperioden som ligger lengst frem i tid. Hvis bruker 
fortsatt er sykmeldt utsetter vi stansmeldingen til 17 dager etter denne nyeste tom-datoen. Hvis bruker ikke lenger er sykmeldt sjekker vi om vi har sendt 4-ukersmelding for dette 
syketilfellet. Hvis vi ikke har sendt 4-ukersmelding avbrytes stansmeldingen (hvis Arena ikke vet om sykefraværet trenger vi ikke sende melding om at syketilfellet er stanset heller). Hvis 
vi har sendt 4-ukersmelding og bruker ikke lenger er sykmeldt sendes stansmeldingen. 

For alle meldinger, både planlagte meldinger og maksdatomeldinger, sjekker vi mot PDL at den sykmeldte ikke er død før meldingen sendes. 

## Technical description
This project contains the application code and infrastructure for sparenaproxy

### Technologies used
* Kotlin
* Ktor
* Gradle

### Getting started
#### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```                                                     
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

#### Building the application
##### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows 
`gradlew.bat shadowJar`

##### Creating a docker image
Creating a docker image should be as simple as `docker build -t "no.nav.syfo" .`

##### Running a docker image
`docker run --rm -it -p 8080:8080 "no.nav.syfo"`
