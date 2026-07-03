# HANDOFF — status og neste steg

> Denne fila ligger i repo-rot og committes MED VILJE (den gamle lå i `docs/`
> som er gitignored, og fulgte derfor ikke med til nye maskiner).
> Oppdater den når en modul er ferdig eller en beslutning tas.

Sist oppdatert: 2026-07-02 (på Mac-en, etter «robusthet i værsøket»-økta).

## Hva appen er

Turvær-/helseapp: finn hvor det blir finest turvær («hvor på Sunnmøre blir det
best vær i helgen?»), planlegg ruta, og følg trening/kosthold/recovery.
Java 25 / Spring Boot 3.5 (Maven) + React (Vite) i `frontend/`.
Auth: Supabase JWT. DB: Postgres via Flyway (`src/main/resources/db/migration`).

## Status per område (planen ↔ koden)

### Værsøk — LENGST FREMME, nettopp gjort globalt + robust
- ✅ Hele kjeden fritekst → tolkning (LLM, fast/smart-tier) → OSM/Overpass
  (topper/turruter i område) → kandidat-utvelgelse (rutenett) → MET-vær →
  scoring m/vekter → ranking. REST: `GET /api/turvaer`, `/api/sted`, `/api/rute`.
- ✅ Landsdekkende → **globalt**: tolkeren gir ISO-landkode, Overpass-søk scopes
  til landet med globalt navnefallback (`OverpassQueries`).
- ✅ NYTT i dag: **Overpass-failover** til speil (overpass-api.de →
  kumi.systems → private.coffee) ved 429/5xx/timeout — adresserer den kjente
  «Kartdata-tjenesten er midlertidig overbelastet»-feilen.
- ✅ NYTT i dag: **vær-fallback til Open-Meteo** (`ResilientWeatherClient`):
  MET først; Open-Meteo (16 dagers horisont) når MET feiler ELLER ikke dekker
  siste dato i perioden. Adresserer «ingen værdata for neste helg» (MET stopper
  på ~9–10 dager; «neste helg» spurt tidlig i uka ligger utenfor).
- ✅ NYTT i dag: retry på geocoding-klienten (samme mønster som MET/Overpass).
- ⚠️ Lisens-merknad: Open-Meteo (geocoding + vær-fallback) er gratis KUN
  ikke-kommersielt. Før kommersiell deploy: kjøp API-plan hos Open-Meteo
  (billig) eller bytt fallback. MET er CC-BY (kommersielt OK, krever attribusjon
  + identifiserende User-Agent — satt i `application.properties`).
- ✅ NYTT i dag: **værsøk → ruteplanlegger-flyt**: «🧭 Planlegg tur hit»-knapp på
  vinneren og på detaljsiden. Stedet løftes til App-state (`planTarget`), tab
  byttes, planleggeren viser banner + oransje målmarkør, sentrerer kartet og
  foreslår rutenavn. ✕ i banneret fjerner målet.

### Ruteplanlegger — grunnmur på plass
- ✅ Klikk-waypoints → rute (`RoutingClient`), høydeprofil (`ElevationClient`),
  kalorier (`CalorieAdvisor`), klesråd (`ClothingAdvisor`), lagring av ruter.
- Gjenstår: terreng/vanskelighetsgrad/utfordringer per tur, highlighting av
  populære stier rundt et valgt resultat, «fortell om turen»-tekst (LLM).

### Trening — fungerer, med kjente feil (se under)
- ✅ Logging av økter (styrke/cardio/hiking), progresjon per øvelse,
  AI-forslag (`WorkoutSuggester`, fast/smart-tier), muskelvelger i UI.
- ✅ NYTT: **treningsplaner i profilen** (`TrainingPlan*`, Flyway V6): «Lagre
  som plan» på AI-forslag, «Mine planer»-liste, «Bruk i ny økt» fyller
  øktbyggeren (planer har samme JSONB-form som økter/forslag). Tak på 30.
- Gjenstår: progressive overload-motor (regelbasert, uten LLM), sleep
  score-tilpasning (krever klokke-integrasjoner), real-time tracking (sen fase).

### Kosthold — kjernen på plass
- ✅ Favoritt-matvarer m/tak (`NutritionFavorite*`, Flyway V4, sikret + testet).
- ✅ NYTT: **kostholdsdagbok** (`MealEntry*`, Flyway V5): søk i hele
  Matvaretabellen (~2100 varer, hentes ÉN gang og caches — null API-kall per
  søk), logg med gram ELLER varens egne porsjoner (glass/skive/dl …),
  dagstotaler (kcal/protein/karbo/fett, snapshot ved logging), og
  **ukesoversikt for 16 vitaminer/mineraler** mot NNR 2023-referanser med
  hardkodede råd når inntaket er lavt (`NutrientReference`).
  Kilde-attribusjon til Matvaretabellen/Mattilsynet vises i UI (NLOD-lisens,
  kommersiell bruk OK med henvisning).
- ✅ NYTT: **kaloriteller med vektmål** (`CalorieGoal*`, Flyway V7): profil
  (vekt/høyde/alder/kjønn/aktivitet/tempo) → daglig kcal-mål via
  Mifflin-St Jeor × aktivitetsfaktor ± 7700 kcal/kg fordelt på uka; gulv på
  1400 kcal/dag og tak på ±1,5 kg/uke. Målet beregnes alltid ferskt (lagres
  ikke), så formel-forbedringer slår inn for alle.
- ✅ NYTT: **trening ↔ kosthold**: `WorkoutCalorieEstimator` (ren MET-tabell,
  løping fartsbasert, hiking m/stigningstillegg, styrke ~3 min/sett) +
  `DailyBalanceService` → `/api/kosthold/dag` viser nå
  «mål − spist + trening = igjen» og et påfyll-råd ved forbrenning ≥ 500 kcal.
- Gjenstår: strekkode (utsatt), egne/publiserte matvarer,
  fremside-sammendrag, habit tracker.

### Recovery/skadeforebygging — ikke startet
- Plan: hardkodet/forhåndsresearchet innhold for de vanligste sportene
  (brukeren gjør research — SPØR om verdier, f.eks. trygge volumøkninger
  per uke, i stedet for å finne på).

### Generelt
- ✅ NYTT: **fremside-sammendrag for kosthold** (klikkbart kort på Hjem):
  dagens balanse (spist/trening/igjen av målet) + ukas lave næringsstoffer
  med kort konsekvens-tekst; klikk går til kosthold-fanen.
- Gjenstår: i18n (engelsk standard, norsk +++, lagres per bruker),
  klokke-integrasjoner (Apple Health/Garmin/Strava/Whoop), prismodell.
  Sikkerhetsprinsipp: lagre minst mulig sensitivt, per-bruker kryptering
  når integrasjonene kommer.

## Kjente feil — status

1. **401 ved lagring av økt** — DIAGNOSTISERT: skjer når backend mangler
   `SUPABASE_JWT_SECRET`/`SUPABASE_JWKS_URI` (da feiler ALL tokenvalidering),
   eller når brukeren ikke er innlogget/tokenet er utløpt. Backend logger nå
   en tydelig feilmelding i stedet for kryptisk dekoder-feil, og frontend
   viser «logg inn på nytt»-melding ved 401. Sjekk oppstartsloggen:
   `Supabase JWT-validering konfigurert: HS256=…, JWKS=…` — står det
   false/false, er env-variablene ikke satt.
2. **«Kartdata-tjenesten overbelastet»** — fikset med speil-failover (i dag).
3. **«Ingen værdata» for helg-søk** — fikset med Open-Meteo-fallback (i dag).
4. **Vite proxy ECONNREFUSED /api/treningsokter** — ikke en bug: backend
   kjørte ikke. Start backend før frontend (se under).

## Oppsett på ny maskin (det som IKKE følger med git)

1. `frontend/.env` — kopier `frontend/.env.example`, fyll inn Supabase-URL
   og anon key (Supabase-dashboardet → Settings → API).
2. Backend-env: `SUPABASE_JWT_SECRET` (eller `SUPABASE_JWKS_URI`),
   `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` (Supabase Postgres),
   evt. LLM-nøkler (se `application.properties` for alle navn).
3. Kjør: `mvn spring-boot:run` (backend, port 8080) + `npm run dev` i
   `frontend/` (Vite proxyer `/api` → 8080).
4. Tester: `mvn test` (124 stk, alle grønne per i dag).

## Foreslåtte neste steg (i rekkefølge)

1. Verifisere 401-fiksen + dagboka + kaloribalansen + fremside-kortet
   ende-til-ende når env-variablene er satt her (Flyway kjører V5-V7
   automatisk ved oppstart).
2. Turdetaljer i ruteplanleggeren (terreng/stigning/vanskelighetsgrad).
3. Habit tracker (koffein/søvn/lesing …) i kosthold eller egen fane.
4. Recovery-fane med hardkodede råd per sport (brukeren gjør research -
   spør etter verdier).

## Arbeidsstil (viktig)

Bygg én modul om gangen, forklar flyten, verifiser med tester før neste steg.
Ikke dump ferdig UI — dette er også et læringsprosjekt. Reduser API-kall
(cache/hardkoding der det gir mening); brukeren gjør gjerne research selv —
spør heller enn å gjette faktaverdier.
