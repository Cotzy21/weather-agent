# Sikkerhetsgjennomgang

Gjennomført 2026-09-30 som en kodegjennomgang der jeg leste appen slik en angriper ville gjort: alle
endepunkter, autentisering, database-, kommando- og HTTP-kall, frontend og oppsett. Jeg har ikke
angrepet den kjørende tjenesten på Render (sandkassen får ikke kontakt med den), så alt under er funnet i
koden og testet lokalt (369 backend-tester, 34 frontend-tester, og en ekte Postgres for migreringen).

## Kort oppsummering

| # | Funn | Alvor | Status |
|---|------|-------|--------|
| 1 | **Supabase Data API åpent for hele databasen.** Tabellene våre har radnivåsikkerhet (RLS) av og full tilgang for rollene `anon`/`authenticated`. Anon-nøkkelen ligger i frontend-koden, så hvem som helst kunne lese og endre alle brukeres økter, vekt, måltider og profiler direkte mot `…supabase.co/rest/v1/<tabell>`, forbi backend. Bekreftet mot en lokal Postgres som etterligner Supabase. | **Kritisk** | Fikset i `V16` (kjøres ved neste deploy). **Se «Det du må gjøre»** |
| 2 | **Lagret XSS via OpenStreetMap-data.** Navn på topper, stier og operatører ble satt inn som HTML i kart-popups. Hvem som helst kan endre OSM, så et manipulert navn kunne kjøre skript i offerets nettleser og stjele innloggingen. | Høy | Fikset (`escapeHtml`) + CSP |
| 3 | **Anon-/service-nøkkel godtatt som innlogging.** Supabase signerer de offentlige `anon`- og `service_role`-nøklene med samme hemmelighet, så de passerte signaturkontrollen. De mangler bruker-id, og ga 500-feil på alle beskyttede endepunkter. | Høy | Fikset (`SupabaseClaimsValidator`: kun `authenticated` + gyldig bruker-UUID) |
| 4 | **Åpne endepunkter uten grenser mot betalte tjenester.** `/api/turvaer` (LLM + Overpass + MET), `/api/sted` og `/api/rute` (delt kartnøkkel) kunne kalles i det uendelige uten innlogging: kostnad, utestenging hos Overpass, tømt kartkvote. | Høy | Fikset (per-IP + global grense, `RateLimitFilter`) |
| 5 | **Minnebombe via ubegrensede cacher.** Cache-nøklene (koordinater, områdenavn) kom fra åpne endepunkter og cachene hadde ingen grense, så noen kunne fylle minnet på en 512 MB-instans og få serveren til å krasje. | Høy | Fikset (Caffeine: maks 500 oppføringer, 6 t; koordinater rundes av) |
| 6 | **Ingen størrelsesgrense på forespørsler.** JSON-body kunne være hvor stor som helst. | Middels | Fikset (`RequestSizeLimitFilter`, 1 MB; Garmin-import 8 MB) |
| 7 | **Masseregistrering av kontoer omgår AI-kvoten.** Hver konto fikk egen kvote, så mange kontoer ga ubegrenset LLM-kostnad. | Middels | Fikset (global døgngrense 1500 + delt IP-grense for AI-endepunktene) |
| 8 | **Ingen tidsavbrudd mot eksterne tjenester.** Én treg oppstrøms tjeneste kunne holde alle tråder opptatt. | Middels | Fikset (`spring.http.client.*`: 5 s tilkobling, 90 s lesing) |
| 9 | **Ubegrenset LLM-svar.** Ingen `max_tokens`, så en manipulert prompt kunne gi uendelig dyre svar. | Middels | Fikset (tak 6000 tokens) |
| 10 | **Mine egne offline-køer blandet brukere.** Køen med økter som venter på nett var ikke knyttet til bruker. En annen bruker på samme enhet kunne sendt forrige brukers økter under sin konto. | Middels | Fikset (køen har eier; andres økter sendes/telles aldri) |
| 11 | **Manglende sikkerhetshoder.** Ingen CSP, HSTS eller Referrer/Permissions-Policy. | Middels | Fikset (`SecurityConfig`) |
| 12 | **Uvalidert input på åpne endepunkter:** koordinater utenfor jorda/NaN, vekt 0 eller 1e9, spørsmål på flere MB, stedsnavn uten lengde. | Middels | Fikset |
| 13 | **Ubegrenset lagring per bruker:** økter per dag, lagrede ruter, vanelogger og måltider med vilkårlige datoer og mengder. | Lav–middels | Fikset (`Limits`, tak per dag/bruker, datointervall, verdigrenser) |
| 14 | **Uventede feil kunne lekke detaljer** og ga 500 på null-verdier. | Lav | Fikset (generisk 500, detaljer bare i loggen; Springs egne 404/405/400 beholdes) |
| 15 | **`/api/innstillinger/**` var ikke beskyttet** (jeg la det til tidligere i dag; testen min fanget det). | Middels | Fikset |
| 16 | LIKE-jokertegn (`%`, `_`) i matsøket kunne tvinge frem tunge søk. | Lav | Fikset |
| 17 | Containeren kjørte som root. | Lav | Fikset (`USER app`) |
| 18 | Sårbare avhengigheter: Spring Boot 3.5.0 (kjente CVE-er i Spring/Tomcat) og 5 npm-pakker (nanoid, postcss, tar). | Middels | Fikset (Spring Boot 3.5.16, `npm audit`: 0 sårbarheter) |
| 19 | Manglende størrelser på plan-/assistent-DTO-er (tittel over 120 tegn ga 500). | Lav | Fikset |

## Det du må gjøre (jeg kan ikke gjøre dette selv)

1. **Deploy `V16` og sjekk det.** Etter deploy: åpne `https://<prosjekt>.supabase.co/rest/v1/workouts` med kun anon-nøkkelen
   (`apikey`-header). Du skal få «permission denied» eller tom liste, aldri data. Har du tidligere delt
   anon-nøkkelen eller åpnet appen offentlig, må du regne med at data kan ha vært lesbar før dette.
   Backend må koble til som databaseeieren (`postgres.<ref>` fra `.env.example`), som omgår RLS. Bruker du en annen
   rolle uten eierskap, slutter appen å virke.
2. **Supabase → Authentication:** slå på e-postbekreftelse, «leaked password protection» og gjerne CAPTCHA på
   registrering, og slå av anonym innlogging hvis du ikke bruker den. Uten dette er registrering åpen for masseopprettelse.
3. **Sett `APP_CORS_ALLOWED_ORIGINS`** i Render til bare appens egen adresse (og mobil-opphavene du bruker).
   Standardlisten inkluderer `http://localhost`.
4. **CSP og eget Supabase-domene:** brukes et eget domene i stedet for `*.supabase.co`, legg det til i `connect-src`
   i `SecurityConfig.CSP`, ellers blokkeres innlogging.
5. **Flett `.github/workflows/keepalive.yml` inn i `main`.** Planlagte GitHub-jobber kjører bare fra standard-branchen.
6. Roter Supabase JWT-hemmeligheten og LLM-/ORS-nøklene hvis noen av dem noen gang har vært i git, logg eller chat.
   Jeg fant ingen hemmeligheter i repoet (`.env` er gitignorert).

## Sjekket og funnet i orden

- **SQL-injeksjon:** kun JPQL med bundne parametre og Spring Data-metoder, ingen `nativeQuery`/strengbygde spørringer.
- **Kommando-injeksjon:** ingen `Runtime.exec`/`ProcessBuilder`, ingen filoperasjoner på brukerinput.
- **Overpass-injeksjon:** områdenavn og landkode escapes (`OverpassQueries.escape`), koordinater er tall.
- **IDOR:** alle oppslag/sletting er scopet på `userId` fra JWT (`findByIdAndUserId`, `deleteByIdAndUserId`).
- **SSRF:** alle utgående kall går til faste verter via URI-byggere (parametre URL-enkodes); ingen bruker-URL hentes.
- **Deserialisering, fil-opplasting, path traversal:** ikke i bruk.
- **CSRF:** ikke relevant (Bearer-token, ingen cookies, ingen sesjon).
- **JWT:** algoritmene er låst (HS256/RS256/ES256), `none` avvises, utløp valideres.
- **ReDoS:** regexene i backend har ingen nestede kvantifikatorer; uttrykket jeg la til for planvalidering er avgrenset (`.{0,50}`).
- **CSP:** den ferdigbygde appen ble lastet i headless Chromium med akkurat denne policyen; ingen brudd i konsollen (skript, stiler,
  fonter, manifest og service worker laster). Kartfliser og innlogging mot Supabase er ikke prøvd (ingen nett/nøkler i sandkassen).
- **React:** ingen `dangerouslySetInnerHTML`, `eval` eller `innerHTML`; eneste HTML-sink var Leaflet-popupene (fikset).

## Kjente restrisikoer (akseptert eller utenfor kode)

- **Rate limit bak proxy:** klient-IP leses fra første `X-Forwarded-For`-verdi, som en angriper kan forfalske for å
  skifte «identitet». De globale grensene (600 værsøk/t, 3000 AI-kall/t, 1500 AI-kall/døgn) er bakstopperen, men en
  målrettet angriper kan bruke opp dem og stenge andre ute i en periode. Grensene ligger i minnet per instans og
  nullstilles ved restart; ved flere instanser trengs en delt teller (f.eks. Redis).
- **Token i `localStorage`** (Supabase standard). Reduseres av CSP og escaping, men et XSS ville fortsatt kunne lese det.
- **Offentlig delte matvarer** kan misbrukes til feilinformasjon eller spam. Det finnes ikke moderering (se `DEPLOY.md`).
- **Prompt-injeksjon** via fritekst (skader, notater, foretrukne øvelser) påvirker bare brukerens egne planer, ikke andres.
  Svaret parses som JSON, valideres mot utstyr/liker-ikke og vises som tekst (React escaper).
- **Personlig e-post** står i `met.user-agent` i `application.properties` (MET krever kontaktinfo). Bytt til en delt adresse om du
  åpner repoet.
- **Render gratisplan** sover (se `DEPLOY.md`). Keep-alive-jobben hjelper, men er ikke garantert.
