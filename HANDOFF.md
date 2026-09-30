# HANDOFF — status og neste steg

> Denne fila ligger i repo-rot og committes MED VILJE (den gamle lå i `docs/`
> som er gitignored, og fulgte derfor ikke med til nye maskiner).
> Oppdater den når en modul er ferdig eller en beslutning tas.

Sist oppdatert: 2026-09-30 (kontosletting, personvern, rapportering av matvarer, strekkodeskanning, «Fortell om turen»,
engelske kostholdsråd, utstyr fra fritekst).
Se også **DEPLOY.md** for alt som må gjøres før første deploy.

## Hva appen er

Turvær-/helseapp: finn hvor det blir finest turvær («hvor på Sunnmøre blir det
best vær i helgen?»), planlegg ruta, og følg trening/kosthold/restitusjon.
Java 25 / Spring Boot 3.5 (Maven) + React (Vite) i `frontend/`.
Auth: Supabase JWT (HS256 + ES256/JWKS). DB: Postgres via Flyway (V1–V18).
Dockerfile + PORT-config for backend-deploy finnes.

## Status mot originalplanen

### Værsøk ✅
- Fritekst → LLM-tolkning (fast/smart-tier) → OSM/Overpass (topper/ruter i
  område, globalt m/landkode) → kandidater → vær → scoring → ranking.
- **Prompten vekter seg selv**: «vi hater regn og vind, temperatur er ikke
  viktig» → regn/vind HØY, temp LAV (brukes når glidebryterne står på standard).
- Robusthet: Overpass-speil-failover, MET → Open-Meteo-fallback (16 dager),
  retry på geocoding. Eksempel-chips i søket. VARSEL-modus for «hvordan blir
  været i X».
- Værsøk → ruteplanlegger («🧭 Planlegg tur hit»).

### Ruteplanlegger ✅ (delvis)
- Waypoints → rute, høydeprofil, kalorier, snacks, klesråd, lagring, kartlag
  (kart/terreng/satellitt).
- **Terrengvurdering** (`RouteAssessor`): DNT-inspirert vanskelighetsgrad,
  høyeste punkt, bratteste parti, «vær forberedt på»-råd.
- **Turstier highlightet**: detaljsiden henter ekte stigeometri
  (`out geom`, klippet til boks rundt stedet, uttynnet — Preikestolen:
  280 KB → 28 KB), tegner linjene og fremhever en sti når man trykker på den.
- **Fortell om turen** (`RouteStoryService`, `POST /api/ruter/fortelling`, krever innlogging): 3–5 setninger om ruta fra
  språkmodellen (FAST-tier). Modellen får BARE ruteplanleggerens egne tall som JSON og er bedt om ikke å finne på
  stedsnavn/vær. Teller mot AI-kvoten per bruker og AI-grensen per IP; knapp under estimatet i ruteplanleggeren.

### Trening ✅
- **Maler og gjennomførte økter er skilt (2026-09-30):** «Ny» (bygg selv, velg muskler, AI-økt, AI-plan) lager bare en MAL i «Mine økter»
  (`POST /api/trening/planer`; byggeren har ikke dato eller notater lenger). En økt regnes som gjennomført først når den er STARTET med
  ▶ Start og fullført: styrke via live-økta (`LiveSession`), alt annet (løping, sykkel, tur, svømming, buldring, kampsport, fristil) via
  `ActivitySession` (klokke, mål fra malen, bekreft distanse/varighet ved fullføring). Begge går gjennom samme offline-kø
  (`POST /api/treningsokter`). Garmin-import er den eneste andre veien til en logget økt.
- **Start-skjermen** (`WorkoutStart.jsx`): tom styrkeøkt, annen aktivitet, «Planlagt i dag», og malene sortert etter **kategori**
  (`workoutCategories.js`: Push, Pull, Bein, Overkropp, Underkropp, Hele kroppen, Kjerne, Annen styrke, og aktivitetstypene) med søk og
  «sist gjort». Kategorien velges i byggeren (lagres som `content.category`, ingen migrering) eller gjettes fra tittel og øvelsenes
  muskelgrupper. Taket på lagrede økter er 100.
- Logging (styrke/cardio/hiking, supersett m/runder, dra-og-slipp), progresjon,
  Garmin CSV-import (ekte kalorier), treningsplaner i profilen, AI-assistent
  (samtale m/oppfølgingsspørsmål → ukeplan man aksepterer/forkaster).
- **Progressive overload-motor** (`ProgressionAdvisor`, regelbasert, 0 LLM):
  dobbel progresjon per øvelse — reps opp til toppen av området, så vekt
  (+5 kg store beinløft / +2,5 kg / +1 kg under 20 kg), deload −10 % etter
  tre økter uten fremgang. «Neste gang»-kort i oversikten, «+ Legg i økt».
- **Dagsform fra søvn** (`ReadinessAdvisor`): søvn-vanen i habit trackeren →
  god/middels/lav (7 t / 6 t-grenser) med råd; sendes også til AI-assistenten.
  Byttes mot klokkens søvnscore/HRV når integrasjonene kommer.
- **Onboarding / treningsprofil** (Flyway V14, `TrainingProfileService`): første gang
  man åpner Trening spør en 4-stegs sheet om erfaringsnivå, hvor lenge/hvor jevnt man
  har trent, mål, utstyr, dager/tid, teknikk-trygghet, skader, styrker/svakheter og
  forklaringsstil (kan hoppes over, og redigeres fra Progresjon → treningsminnet).
  Profilen legges i **hver** AI-prompt sammen med nivåspesifikke regler (nybegynner →
  enkle øvelser, lavt volum, teknikktips). **Modellvalg:** nybegynner/«litt erfaren»,
  usikker teknikk eller skader → `LlmTier.PRO` (`llm.model.pro`, faller tilbake til
  smart); ellers SMART. `GET/PUT /api/trening/profil` (204 = ikke onboardet).
- **Kvalitet på AI-planer** (`PlanValidator`): etter at planen er laget sjekkes den regelbasert mot
  utstyr (profilens `equipment` + `requires` i øvelseskatalogen), liker-ikke-lista, tung bein samme
  dag/dagen før kampsport og styrke på kampsportdager når brukeren ba om det. Ved brudd: ETT retry
  (PRO-modell) med konkrete rettelser, deretter fjernes øvelser som fortsatt bryter utstyr/liker-ikke,
  og resten blir en ⚠-advarsel i sammendraget. Koster ett ekstra AI-kall kun ved brudd.
  **Utstyr leses også fra fritekst** (`EquipmentNotes`): «jeg har bare kettlebells», «kun manualer», «no cable machine»,
  «ingen stang», «ingen utstyr». «Bare/kun/only + utstyr» = alt hun har (manualer regnes med benk, kettlebells som manualer),
  «ingen/uten/no + utstyr» fjerner akkurat det, og teksten går foran profilen. Ikke dekket: fritekst i profilen (skader
  o.l.) og notater i treningsminnet.
- **Øvelseskatalog** (`src/main/resources/exercises.json`, delt med frontend): faste id-er med nb/en-navn
  og aliaser. Progresjon, historikk, minne og «neste sett» grupperer på id (`ExerciseCatalog.groupKey`),
  så «Bicep Curls» og «Bicepscurl» er samme øvelse. Nye økter får `exerciseId` på blokkene; gamle økter
  trenger ingen migrering fordi gruppering slår opp på navn ved lesing. Legg nye øvelser i JSON-fila.
- **AI-kvote** (`AiRateLimiter`): 20 kall/time og 60/døgn per bruker (`ai.limit.per-hour`/`per-day`),
  429 med `Retry-After`. Latens per LLM-kall logges. Streaming er ikke bygget (se IMPROVEMENTS.md).
- **Program med startdato/lengde** (`user_settings`, V15): `GET/PUT/DELETE /api/trening/program`,
  «Dag 12/56» i ukekortet. **Dashboard-oppsett** lagres i kontoen (`/api/innstillinger/dashboard`).
- **Offline** (`offlineQueue.js`, `sw.js`): fullførte økter lagres i IndexedDB med `clientId` og sendes
  når nettet er tilbake (backend er idempotent på `clientId`); treningsdata ligger også lokalt, og
  service worker cacher app-skallet. Økter kobles til planer på `plannedId` (tittel som reserve).
- **Tester/CI:** Vitest i frontend (`npm test`), GitHub Actions i `.github/workflows/ci.yml`.
- **Sikkerhet** (se `SECURITY.md` for hele gjennomgangen): RLS/rettigheter i Supabase (`V16`), JWT må tilhøre en innlogget
  bruker (`SupabaseClaimsValidator`), per-IP og global rate limit (`RateLimitFilter`, `AiRateLimiter` med global døgnkvote),
  størrelsesgrense på forespørsler, begrensede cacher, CSP/HSTS-hoder, tidsavbrudd og `max_tokens` mot LLM, generiske
  feilsvar, escapede kart-popups, container som ikke-root, Spring Boot 3.5.16. Keep-alive mot Render:
  `.github/workflows/keepalive.yml` (må ligge i `main`).
- **Garmin-import** (knapp «Importer fra Garmin» synlig øverst på Trening, samt i «＋ Ny»-menyen):
  - **CSV** (alle aktiviteter): sendes i biter på 250 rader (`csvChunks.js`), så hundrevis av økter ikke tar alt på én gang
    (Render 512 MB, tidsavbrudd). Lagrer nå også totaler for styrkeøkter (sett, reps, puls) og dedupe bruker en lett nøkkelspørring.
    CSV har ALDRI øvelser eller vekter; det er en begrensning hos Garmin.
  - **FIT / ZIP** (`garminZip.js`, `garminFit.js`, `garminImport.js`): øvelser, reps og vekter per sett. Zip (også zip i zip, som
    Garmins fulle dataeksport) leses strømmende og FIT tolkes i nettleseren; serveren får bare ferdige økter (`POST /api/trening/import/fit`,
    små grupper, idempotent på `clientId` = hash av starttid). En FIT-økt fyller inn øvelser på CSV-økten fra samme dag (nærmeste varighet)
    i stedet for å lage duplikat, og omvendt. Dette er løsningen på 512 MB-problemet: all tung regning skjer på telefonen/PC-en.
  - **Progresjon**: «Progresjon»-fanen har «Utvikling per øvelse» (første → siste, % endring, trendlinje; regnet ut i nettleseren over hele
    historikken) og «Økter over tid» per økttype (Push/Pull/Legs) fra CSV-totaler. Garmin-øvelsesnavn er lagt som aliaser i `exercises.json`,
    så de samles med øvelsene du logger selv. Manual- og stangvarianter holdes bevisst adskilt.
  - **Øvelseskobling med AI** (`ExerciseMatchService`, `POST /api/trening/ovelser/koble`, Flyway V17): etter at FIT-filene er lest samles de
    UNIKE øvelsesnavnene. Katalogen (navn + aliaser) kobler det den kjenner, uten nett og uten kostnad; resten går til ÉN LLM-spørring
    (`FAST`-modellen, f.eks. `LLM_MODEL_FAST=gpt-4o-mini` mot OpenAI) som bare kan velge blant katalogens id-er. Svaret lagres per bruker
    (`exercise_name_map`, også «ingen treff»), så samme navn aldri spørres om to ganger. Øktene skrives så under appens navn på valgt språk.
    Feiler LLM-en (ingen nøkkel, kvote, nett) beholdes Garmins navn og importen fortsetter. Krever `LLM_BASE_URL`/`LLM_API_KEY` i Render.
  - Ikke verifisert mot en ekte Garmin-eksport (bare FIT-filer laget med Garmins egen koder i tester). Lisens for `@garmin/fitsdk`: se `THIRD_PARTY.md`.
- Gjenstår: real-time tracking (sen fase i planen), klokke-integrasjoner.

### Kosthold ✅
- Dagbok mot Matvaretabellen (~2100 varer, cachet), kalorimål (Mifflin-St Jeor),
  trening ↔ kosthold-balanse, 16 vitaminer/mineraler per uke mot NNR 2023,
  favoritter + egne måltider (synkes til konto), fremside-kort.
- **Egne matvarer** (Flyway V10): navn/merke/strekkode/næring per 100 g/porsjon,
  privat eller **delt offentlig**; søket slår sammen egne + delte +
  Matvaretabellen (merket «egen»/«delt»). `GET /api/kosthold/strekkode/{kode}`
  er klart for en kamera-skanner.
- **Flere enheter**: kg, dl, ml, spiseskje, teskje (+ varens egne porsjoner).
- **Preferanser & allergier** (Flyway V11): diett (alt/vegetar/pescetar/vegan),
  14 EU-allergener, «liker ikke». `FoodFlags` gjenkjenner dem i matnavn
  (norske sammensatte ord, unntakslister). Søket merker og legger uegnede
  varer bakerst; **næringsrådene filtrerer kildene** (melkeallergiker får
  grønnkål/plantedrikk for kalsium, veganer beriket drikk/tilskudd for B12).
  UI sier alltid «basert på navnet – sjekk pakningen».
- **Strekkodeskanning** (`BarcodeScanner.jsx`, `barcode.js`; 📷 ved matsøket): kameraet leser koden med `BarcodeDetector`
  (Chrome/Android). iPhone/Safari har ikke `BarcodeDetector`, så der skriver man koden inn (feltet finnes alltid).
  `GET /api/kosthold/strekkode/{kode}` (`BarcodeLookupService`) leter i egne + delte varer, så hos **Open Food Facts**
  (`OpenFoodFactsClient`, ODbL, kreditert i bunnteksten). Et OFF-treff lagres som en PRIVAT egen matvare med strekkoden, så
  dagboka logger den som alle andre egne varer og neste skanning ikke trenger eksternt kall. OFF-data er dugnadsdata og
  valideres med samme grenser som egne varer; utfall/feil hos OFF betyr bare «ikke funnet». `OPENFOODFACTS_ENABLED=false`
  skrur det av. Ukjent strekkode åpner «egen matvare» med strekkoden utfylt. Ikke prøvd med ekte kamera (bare logikk og
  typet inntasting er testet).
- **Rapportering og moderering av delte matvarer** (Flyway V18 `food_reports`): ⚑ ved varer andre har delt (grunn +
  kommentar), én rapport per bruker og vare, aldri egne/private varer, maks 100 per bruker. En rapport skjuler ingenting av
  seg selv. Administratorer (Supabase-bruker-id-er i `APP_ADMIN_USER_IDS`, tom = ingen) ser «Moderering» på kontosiden og kan
  slette varen eller avvise rapportene (`/api/admin/**`, 403 for alle andre).

### Restitusjon ✅
- ACWR-skadevarsler per aktivitet, restitusjonssteg per økttype, hviledag-
  forslag, habit tracker (7-dagers rutenett, streaks). Råd på nb/en fra backend.
- Gjenstår: recovery-verktøy (massasjepistol, basseng …) — planen sier
  «vi finner ting når den tiden kommer».

### Generelt
- i18n: nb/en i hele frontenden, engelsk standard, 🌐-knapp. Restitusjon og
  progresjonsråd kommer også på engelsk fra backend (`?lang=en`), og nå også kostholdsrådene (navn, «lavt inntak»-tekst, kildeliste
  og treningsdag-tipset via `NutrientTranslations`; allergifilteret kjører fortsatt på de norske matnavnene).
- Gjenstår: **flere språk** («alle de mest populære») — bestem hvilke;
  klokke-integrasjoner (Apple Health/Garmin/Strava/Whoop); prismodell.
  Sikkerhetsprinsipp: minst mulig sensitivt lagret, per-bruker kryptering
  når integrasjonene kommer.

### Konto, personvern og drift ✅ (nytt 2026-09-30)
- **Kontosletting** (`DELETE /api/konto`, «Slett kontoen min» på kontosiden, bekreftes ved å skrive SLETT/DELETE):
  `UserDataEraser` sletter alt brukeren eier i alle 17 tabeller i én transaksjon; `UserDataEraserTableCoverageTest` feiler hvis
  en ny JPA-entitet ikke står i lista (legg nye brukertabeller i `OWNED_TABLES`). Deretter forsøker `AuthUserRemover` å slette
  innloggingen i Supabase (`auth.users`, backend kobler til som databaseeier). Best effort: feiler det, får brukeren beskjed
  om at e-posten må fjernes på annen måte. Frontend rydder også cache, live-økt, dashboard-oppsett og økter som venter på nett.
- **Personvernerklæring** (`PrivacyView.jsx`, nb/en) og **samtykke** (avkrysning kreves for å registrere seg, helsedata =
  GDPR art. 9). Teksten er skrevet ut fra hva appen faktisk lagrer og kaller, men er et **UTKAST**: les den gjennom, sett
  `REVIEWED = true` i `PrivacyView.jsx` og `VITE_CONTACT_EMAIL` i `frontend/.env` (kontaktadresse vises bare hvis satt).
  Bekreft også hvilken Supabase-region dataene ligger i (teksten nevner ingen region).
- **Kildehenvisninger** i bunnteksten (MET og Open-Meteo CC BY 4.0, OpenStreetMap, Matvaretabellen, Open Food Facts) og
  «ikke medisinsk råd» i Kosthold.
- **`GET /api/health`** (åpen, uten avhengigheter): bruk den til oppetidsovervåking. Ikke Actuator med vilje.

### Engasjement og vaner 🚧 (påbegynt 2026-09-30)
- Planen ligger i `ENGAGEMENT.md` (bygget på forskningsrapporten som ligger i Hindsight som `research/engasjement/*`, start med `00-indeks.md`).
  Prinsipper: tilgivende ukebasert serie, feir bare ekte tall, comeback framfor skyld, få varsler, aldri mørke mønstre.
- **Ukeserie** (0.1, `weeklyStreak.js`, `StreakCard.jsx`, kort «Ukeserie» på Hjem): mål 1–7 treningsdager/uke, hvilekort (1 per 4 gode uker, maks 2),
  pause for sykdom/ferie, reparasjon med mål+1 uka etter en glipp, comeback. Regnes i nettleseren fra øktene; mål og pauser lagres som
  innstillingen `streak` (`/api/innstillinger/streak`, validert i `UserSettingsService`).
- **Rekorder** (0.2, `prDetection.js`, `PrCelebration.jsx`): estimert 1RM- og rep-rekord mot egen historikk, 🏆 på rekordsett i live-økta og
  feiring etter lagring. Første gang en øvelse logges er aldri en rekord.
- Neste i planen: 0.4 daglig restitusjonsinnsjekk, 0.5 volumvarsel koblet til serien, 0.6 vane-tracker «aldri to på rad», 0.7 kostloggingsserie
  (trenger et endepunkt for loggede dager), 0.3 heatmap, 0.12 varsler (trenger native varsler i iPhone-appen).

### iPhone-app (Capacitor) 🚧 (påbegynt 2026-09-30)
- `frontend/ios/` er et ferdig Xcode-prosjekt (Capacitor 8, Swift Package Manager): bundle-ID `no.weatheragent.turvaer`, «Turvær», kun iPhone og
  stående, ikon og oppstartsbilde fra `app-icon.svg`. `npm run ios:sync` bygger web-appen i native modus (`--mode native` → `dist-native/`,
  API-adressen fra `.env.native`) og kopierer den inn. Backend-CORS tillater `capacitor://localhost` (sjekket mot produksjon).
- `.github/workflows/ios.yml`: `build` kompilerer for simulator på en macOS-maskin (uten signering); `testflight` bygger, signerer og laster
  opp (manuelt, trenger Apple-hemmeligheter, **ikke prøvd**). Personvern-URL for App Store: `/#personvern`.
- Alt om oppsett, Apple-trinn, App Store-sjekkliste og kjente begrensninger: `frontend/MOBILE.md`.
- Gjenstår: Apple Developer-konto + App Store Connect-oppføring, kjøre på ekte iPhone, strekkodeskanner i WKWebView (polyfill/plugin),
  demo-konto til App Review, «glemt passord», dyplenker for e-postbekreftelse, betalt Render, Android.

## Kjente feil — status

1. **401 ved lagring** — fikset (ES256-tokens + tydelig logg når
   `SUPABASE_JWT_SECRET`/`SUPABASE_JWKS_URI` mangler).
2. **«Kartdata-tjenesten overbelastet»** — speil-failover. NB: fra enkelte
   nett svarer speilene ikke (sett i sandkasse 2026-09-29) — hovedinstansen
   med retry fungerte.
3. **«Ingen værdata» for helg-søk** — Open-Meteo-fallback.
4. **Vite proxy ECONNREFUSED** — ikke en bug: backend kjørte ikke.
5. **Deploy stoppet på V16** (2026-09-30: `canceling statement due to statement timeout` på
   `alter table flyway_schema_history …`): RLS-løkka tok med Flyways egen historikktabell, som Flyway selv har låst.
   Rettet (V16 hopper over den) og sikret med `MigrationLockSafetyTest`; verifisert ved å starte appen mot ekte Postgres.
   Se SECURITY.md. Lockdownen av Supabase Data API er først aktiv når en deploy med rettet V16 har gått gjennom.
6. **Værsøket hang / ga tom 502 når Overpass var treg** (2026-09-30, funnet i live-test): Render kutter forespørsler etter ca. 100 s, og
   Overpass-klienten kunne bruke 3 speil x 2 forsøk x 90 s. Nå (`OverpassClient.raceMirrors`): 30 s per kall, neste speil
   PARALLELT etter 6 s (med en gang hvis et feiler), første svar vinner, og et tak på 35 s for hele oppslaget. Gir det opp, får
   brukeren 503 med «Kartdata-tjenesten (OpenStreetMap) er travel …» (`OverpassUnavailableException`). Turruter rundt steder er et
   tillegg: er Overpass travel vises værsvaret uten turer (stedsside og vanlig søk); topper/ruter til selve rangeringen er
   nødvendige og gir den vennlige feilen. Testet mot ekte nettverksfeil (stedsside med Overpass utilgjengelig: 200 etter 36 s
   i stedet for å henge).
   **Oppfølging samme dag (krasj i produksjon):** rett etter utrullingen ga `/api/turvaer` en tom 502 etter 30 s og hele
   tjenesten gikk ned i ca. 5 minutter (Render: `hibernate-wake-error`, kom seg selv). Årsak (gjenskapt lokalt med
   `-Xmx330m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError`, samme som Render, og falske speil som sender 12 MB sakte):
   speil som tapte kappløpet ble aldri avbrutt og fortsatte å laste ned og gjøre svaret om til et JSON-tre, og 3 parallelle
   kall x flere brukere ga `OutOfMemoryError` (forrige bygg døde på 18 s). Nå: tapende/hengende kall avbrytes, høyst 3
   Overpass-kall om gangen i hele appen (flere får «travelt» med en gang), svar over 4 MB avvises før de leses inn
   (`ResponseSizeLimit`), og en 200 med HTML/ugyldig JSON regnes som et speil som ikke virker (neste speil prøves).
   Samme stresstest mot det nye bygget: 9 samtidige oppslag, alle 200 på 8-10 s, ingen OOM. `OVERPASS_ENDPOINTS`
   (kommaseparert) overstyrer speilene uten ny kode.
   **Kortere frist for turruter** (samme dag): live-testen viste at stedssiden ventet hele 35 s for å vise en tom turliste når
   kartspeilene var nede. Turruter rundt et sted (`trailsNear`, `trailGeometriesNear`) har nå egen frist: 10 s totalt, neste
   speil etter 3 s (`OPTIONAL_BUDGET_MS`, `OPTIONAL_HEDGE_DELAY_MS`). Topper og ruter i området som rangeringen trenger beholder
   de 35 s. Sikret med `OverpassOptionalBudgetTest` (mot en lokal server som aldri svarer).

## Oppsett på ny maskin (det som IKKE følger med git)

1. `frontend/.env` — kopier `frontend/.env.example`, fyll inn Supabase-URL og
   anon key. Kjør `npm install` i `frontend/`.
2. Backend-env: `SUPABASE_JWT_SECRET` (eller `SUPABASE_JWKS_URI`),
   `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, LLM-nøkler (se
   `application.properties`).
3. Kjør: `mvn spring-boot:run` (8080) + `npm run dev` i `frontend/`.
4. Tester: `mvn test` (496 stk, alle grønne 2026-09-30) og `npm test` i `frontend/` (129 stk). Repository-testene
   kjører mot in-memory H2 (kun test-scope). **Claude Code i skyen** har JDK 21 mens `pom.xml` sier `java.version` 23: kjør
   `mvn -B test -Djava.version=21` der (CI bruker riktig JDK og ga samme resultat). En ekte PostgreSQL 16 finnes i skymiljøet
   og bør brukes til å prøve nye Flyway-migreringer og native SQL (H2 klarer ikke jsonb m.m.).
5. Nye miljøvariabler: `APP_ADMIN_USER_IDS` (moderatorer, kommaseparert Supabase-UUID-er), `OPENFOODFACTS_ENABLED`
   (standard true), `VITE_CONTACT_EMAIL` (frontend, kontaktadresse i personvernerklæringen).

## Foreslåtte neste steg

1. **Importer Garmins fulle dataeksport** (Kontoinnstillinger → Datahåndtering → Eksporter data, kommer på e-post) via
   «Last opp FIT / ZIP» — FIT-/ZIP-importen er aldri prøvd på en ekte eksport. Sjekk med én aktivitet først («Eksporter
   original») og sammenlign med «Eksporter splits til CSV» for samme økt.
2. Les gjennom **personvernerklæringen** (sett `REVIEWED`, `VITE_CONTACT_EMAIL`), sett `APP_ADMIN_USER_IDS`, og gå gjennom
   **DEPLOY.md** («Må gjøres» + SECURITY.md «Det du må gjøre»).
3. Velg hvilke ekstra språk (f.eks. svensk/tysk/spansk/fransk).
4. Prøv strekkodeskanneren på en ekte telefon (Android/Chrome); vurder en zxing-polyfill for iPhone.
5. Ikke bygget, venter på beslutning/maskinvare/eksterne kontoer: klokke-integrasjoner (Apple Health/Garmin/Strava/Whoop),
   Android-appen (iPhone er påbegynt, se over), Kartverket Turrutebasen/Naturbase, lokal LLM på desktopen,
   prismodell, streaming av AI-svar (mål latens først), sanntidssporing, restitusjonsverktøy.

## Arbeidsstil (viktig)

Bygg én modul om gangen, forklar flyten, verifiser med tester før neste steg.
Reduser API-kall (cache/hardkoding der det gir mening); brukeren gjør gjerne
research selv — spør heller enn å gjette faktaverdier.
