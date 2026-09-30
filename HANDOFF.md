# HANDOFF — status og neste steg

> Denne fila ligger i repo-rot og committes MED VILJE (den gamle lå i `docs/`
> som er gitignored, og fulgte derfor ikke med til nye maskiner).
> Oppdater den når en modul er ferdig eller en beslutning tas.

Sist oppdatert: 2026-09-29 (avsjekk mot originalplanen + 5 nye moduler).
Se også **DEPLOY.md** for alt som må gjøres før første deploy.

## Hva appen er

Turvær-/helseapp: finn hvor det blir finest turvær («hvor på Sunnmøre blir det
best vær i helgen?»), planlegg ruta, og følg trening/kosthold/restitusjon.
Java 25 / Spring Boot 3.5 (Maven) + React (Vite) i `frontend/`.
Auth: Supabase JWT (HS256 + ES256/JWKS). DB: Postgres via Flyway (V1–V11).
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
- Gjenstår: «fortell om turen»-tekst (LLM — koster per kall, se prismodell).

### Trening ✅
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
- Gjenstår: kamera-strekkodeskanning (+ ev. Open Food Facts som kilde, ODbL),
  «rapporter»-knapp for offentlige matvarer (se DEPLOY.md).

### Restitusjon ✅
- ACWR-skadevarsler per aktivitet, restitusjonssteg per økttype, hviledag-
  forslag, habit tracker (7-dagers rutenett, streaks). Råd på nb/en fra backend.
- Gjenstår: recovery-verktøy (massasjepistol, basseng …) — planen sier
  «vi finner ting når den tiden kommer».

### Generelt
- i18n: nb/en i hele frontenden, engelsk standard, 🌐-knapp. Restitusjon og
  progresjonsråd kommer også på engelsk fra backend (`?lang=en`); kostholds-
  rådene er fortsatt bare norske.
- Gjenstår: **flere språk** («alle de mest populære») — bestem hvilke;
  klokke-integrasjoner (Apple Health/Garmin/Strava/Whoop); prismodell.
  Sikkerhetsprinsipp: minst mulig sensitivt lagret, per-bruker kryptering
  når integrasjonene kommer.

## Kjente feil — status

1. **401 ved lagring** — fikset (ES256-tokens + tydelig logg når
   `SUPABASE_JWT_SECRET`/`SUPABASE_JWKS_URI` mangler).
2. **«Kartdata-tjenesten overbelastet»** — speil-failover. NB: fra enkelte
   nett svarer speilene ikke (sett i sandkasse 2026-09-29) — hovedinstansen
   med retry fungerte.
3. **«Ingen værdata» for helg-søk** — Open-Meteo-fallback.
4. **Vite proxy ECONNREFUSED** — ikke en bug: backend kjørte ikke.

## Oppsett på ny maskin (det som IKKE følger med git)

1. `frontend/.env` — kopier `frontend/.env.example`, fyll inn Supabase-URL og
   anon key. Kjør `npm install` i `frontend/`.
2. Backend-env: `SUPABASE_JWT_SECRET` (eller `SUPABASE_JWKS_URI`),
   `SPRING_DATASOURCE_URL/USERNAME/PASSWORD`, LLM-nøkler (se
   `application.properties`).
3. Kjør: `mvn spring-boot:run` (8080) + `npm run dev` i `frontend/`.
4. Tester: `mvn test` (280 stk, alle grønne 2026-09-29). Repository-testen
   for egne matvarer kjører mot in-memory H2 (kun test-scope).

## Foreslåtte neste steg

1. **Verifiser ende-til-ende mot ekte DB** — Flyway kjører V10 (egne matvarer
   + utvidet `meal_entries.food_id`) og V11 (preferanser) ved oppstart.
2. **DEPLOY.md** — rate limiting, Supabase RLS, lisenser, personvern.
3. Velg hvilke ekstra språk (f.eks. svensk/tysk/spansk/fransk).
4. Strekkode: kamera-skanning i nettleseren (BarcodeDetector) + oppslag.

## Arbeidsstil (viktig)

Bygg én modul om gangen, forklar flyten, verifiser med tester før neste steg.
Reduser API-kall (cache/hardkoding der det gir mening); brukeren gjør gjerne
research selv — spør heller enn å gjette faktaverdier.
