# Forbedringer — forberedt plan

Status: **bygget** (se HANDOFF.md for hva som ble gjort), bortsett fra punktene under «Ikke gjort». Hvert punkt har problem, løsning, berørte filer, størrelse
(S ≈ timer, M ≈ 1–2 dager, L ≈ flere dager) og åpne spørsmål. Anbefalt rekkefølge nederst.

---

## 1. AI-en følger ikke føringene i planen  (M)

**Problem.** Planen droppet styrke på BJJ-dager, la tung beintrening dagen før BJJ, og foreslo
Face Pulls med bare manualer. Reglene står i prompten (`COMMON_RULES`/`PLAN_RULES` i
`WorkoutSuggester`), men modellen overholder dem ikke, og ingenting sjekker resultatet.

**Løsning: sjekk planen etter at den er laget (regelbasert, 0 LLM) og be modellen rette feil.**
1. Ny `PlanValidator` (ren funksjon, lett å teste) som returnerer en liste med brudd:
   - **Utstyr:** øvelse som krever utstyr brukeren ikke har (fra `TrainingProfile.equipment` og
     notater). Trenger et `requires`-felt per øvelse i et felles øvelsesbibliotek (se punkt 2) —
     f.eks. Face pulls/nedtrekk/kabel → `cable`, leg press → `machine`.
   - **Ukedager:** hver økt i brukerens ønskede dager finnes; ingen tung bein (`BIG_LOWER_BODY`
     finnes allerede i `ProgressionAdvisor`) samme dag som eller dagen før KAMPSPORT/lagsport.
   - **Ikke-liker-lista** fra treningsminnet (`disliked`) og maks antall dager/minutter fra profilen.
2. `WorkoutSuggester`: etter parsing, kjør validatoren. Ved brudd: **ett** retry-kall med
   bruddene som konkrete instrukser («fjern Face pulls, bruker har bare manualer»). Fortsatt brudd →
   fjern/bytt øvelsen deterministisk der det er mulig, ellers returner planen med en advarsel i
   `summary` i stedet for å feile.
3. Modellvalg: bruker allerede `tierFor` (PRO for nybegynnere). Retry-kallet bruker PRO uansett.
4. Tester: `PlanValidatorTest` med de tre feilene fra live-testingen som regresjonstester.

**Åpent:** hvor mye tid skal vi bruke på retry (koster ett ekstra AI-kall, +3–9 s)? Forslag: bare når
validatoren finner brudd, som antas å være under 20 % av kallene.

---

## 2. Øvelsesnavn på to språk  (L)

**Problem.** «Bicep Curls» og «Bicepscurl» lagres som ulike strenger. `ProgressionAdvisor`,
`TrainingMemoryService.topExercises` og historikk grupperer på `name.toLowerCase()`, så progresjonen
og «mest brukte» deles i to. Biblioteket i `frontend/src/exercises.js` har norsk/engelsk i parallelle
lister uten ID.

**Løsning: faste øvelses-ID-er.**
1. **Katalog:** flytt biblioteket til én delt kilde (`exercises.json`: `id`, `group`, `nb`, `en`,
   `requires`, `muscles`, aliaser). Backend leser den ved oppstart; frontend bygger fra samme fil.
2. **Lagring:** øvelsesblokker i `workouts.content` får `exerciseId` ved siden av `name`. Fritekst-øvelser
   uten treff får ingen ID og grupperes på normalisert navn som i dag.
3. **Normalisering:** `ExerciseCatalog.resolve(name)` slår opp på nb/en/alias (små bokstaver, uten
   mellomrom/bindestrek/«s»-endelser) og returnerer ID. Brukes ved lagring og av AI-svar
   (`normalizeSets`-stedet i `WorkoutSuggester`).
4. **Bruk ID overalt:** `ProgressionAdvisor`, `topExercises`, liker/liker ikke, `ExerciseHistory`
   og «bytt øvelse» grupperer på ID; visning bruker navnet i brukerens språk.
5. **Migrering (Flyway V15 + engangsjobb):** gå gjennom eksisterende økter, sett `exerciseId` der
   navnet matcher katalogen. Kjøres idempotent, og uten treff røres ingenting.
6. AI-prompten får listen over kanoniske navn i valgt språk, så modellen bruker dem.

**Risiko.** Berører progresjon, historikk, minne og live-økt. Bygg katalog + resolve + tester først,
og bytt over ett sted om gangen. Migreringen må være reversibel (navn beholdes).

**Åpent:** skal brukerens egne øvelser få ID (generert), eller bare normalisert navn?

---

## 3. Treg og ubeskyttet AI  (M)

**Problem.** Svar tar 3–9 s, og en bruker kan gjøre ubegrenset mange kall (kostnad, misbruk).

**Løsning A — beskyttelse (S, gjør først):**
- Rate-limit per bruker (Bucket4j eller enkel in-memory/Caffeine-teller; ligger allerede `CacheConfig`).
  Forslag: 10 AI-kall/time og 40/dag per bruker, konfigurerbart via `ai.limit.*`. Ved overskridelse: 429 med
  norsk/engelsk melding og «prøv igjen om N min». Gjelder `/api/trening/forslag|plan|assistent`.
- Tak på prompt-størrelse (historikk er allerede begrenset til 8 økter og 20 meldinger).
- Logg tokens/latens per kall (grunnlag for prismodellen i HANDOFF).

**Løsning B — opplevd hastighet (M):**
- **Streaming** av assistent-svaret (SSE) så teksten vises umiddelbart; planen vises når JSON er ferdig.
- Tydelig fremdrift i UI («leser historikken … lager økter …») i stedet for bare spinner.
- Cache identiske forespørsler kort (samme profil + historikk + tekst).
- Mål først: legg latens per tier (FAST/SMART/PRO) i loggen, og vurder mindre modell for enkle
  oppfølgingsspørsmål.

**Merk:** PRO-tier for nybegynnere (nytt) er tregere. Streaming er det som veier opp for det.

**Åpent:** grenser per dag, og skal betalende brukere ha høyere?

---

## 4. Planer mangler lengde og startdato  (M)

**Problem.** `training_plans` har bare tittel/innhold. «Dag 12/56» og ukevisning kan ikke regnes ut.

**Løsning.**
- Flyway V15: `training_plans` får `start_date date`, `weeks int`, `active boolean`. Aktiv plan = maks én per bruker.
- API: `POST /api/trening/planer/{id}/start` (setter startdato, aktiverer, deaktiverer andre),
  `weeks` settes ved lagring (AI foreslår antall uker, brukeren kan endre).
- Beregning i ren funksjon `PlanProgress.of(plan, today)` → `dag N av M`, uke N, ferdig/ikke startet.
- UI: «Dag 12/56» og fremdriftsbar i `WeekProgram`/Hjem-kort; «Start plan i dag/mandag».
- Kobler til punkt 6b: hver planlagt økt får en stabil `plannedId`.

**Åpent:** én aktiv plan om gangen (forslag: ja), og hva skjer etter siste uke (forleng/ny plan/deload)?

---

## 5. Svak dekning på treningssenteret (offline)  (L)

**Problem.** En økt kan ikke fullføres uten nett. `liveSession.js` lagrer allerede pågående økt i
`localStorage`, men lagring til serveren feiler ved dårlig dekning.

**Løsning: offline-kø.**
1. **Kø i IndexedDB** (ikke localStorage, som er lite og synkront): fullførte økter legges i køen med
   klient-generert `clientId` (UUID) før nettverkskallet.
2. **Idempotent server:** `POST /api/treningsokter` godtar `clientId` og returnerer eksisterende økt ved
   gjentak (unik indeks på `(user_id, client_id)`, Flyway V15). Trygt å prøve på nytt.
3. **Synk:** prøv på nytt ved `online`-event, ved app-start og periodisk med backoff. Vis «1 økt venter på
   nett» i UI og ikke fjern noe før serveren har bekreftet.
4. **Service worker** (Workbox/vite-plugin-pwa): cache app-skallet og `GET`-data (`cachedGet` finnes),
   slik at live-økten åpner uten nett. `manifest.webmanifest` og Capacitor finnes allerede.
5. Tester: kø + idempotens (backend), køen mot en falsk `fetch` (frontend, se punkt 6c).

**Åpent:** Capacitor-appen (native) eller bare PWA først? Forslag: PWA først, samme kø fungerer i begge.

---

## 6. Mindre ting

### a) Dashboard-oppsett lagres bare på én enhet  (S)
`dashboardLayout.js` bruker `localStorage`. Flytt til server: Flyway V15 `user_settings (user_id, key,
value jsonb)` (gjenbrukbar for flere innstillinger), `GET/PUT /api/innstillinger/dashboard`.
Frontend leser cache først og synker (samme mønster som kosthold-favoritter i `NutritionView`, som
allerede har engangs-migrering fra localStorage).

### b) Planlagte og loggede økter kobles bare på tittel  (S–M)
`WeekProgram.jsx:34` sammenligner `w.title` og `p.title` (case-insensitivt). Endre tittel og koblingen
brytes; to like titler slås sammen. Løsning: når en økt startes fra en plan, lagre `plannedId` i
`workouts.content` (evt. egen kolonne) og koble på den; behold tittel-match som reserve for gamle økter.
Avhenger av planidentitet fra punkt 4.

### c) Frontend har ingen automatiske tester  (S først, så løpende)
Legg til Vitest + Testing Library og en `npm test`-jobb. Start med ren logikk som er lett å teste og
lett å bryte: `trainingStats.js` (`dayIndex`, `strengthVolume`), `dashboardLayout.js`, `liveSession.js`,
`i18n` (manglende nøkler), og den nye offline-køen. Legg til `npm test` og lint i CI (finnes det
CI? Ingen `.github/workflows` i repoet nå — se åpent).

### d) Render gratisplan bruker ~1 minutt på å våkne  (S)
Enkleste: betalt instans (ingen søvn). Gratis-alternativ: ekstern helsepinging hvert 10. minutt
(UptimeRobot mot `/actuator/health` eller tilsvarende) — fungerer, men bryter Renders vilkår
avhengig av bruk og gir ingen garanti. I tillegg: vis «Vekker serveren …» i frontend når første
kall tar over ~3 s, og hold `Dockerfile`-oppstarten rask (sjekk Spring lazy init/AppCDS).
Se `DEPLOY.md`.

---

## Anbefalt rekkefølge

| # | Punkt | Størrelse | Hvorfor da |
|---|-------|-----------|-----------|
| 1 | 3A Rate-limit | S | Billig, beskytter kostnad før flere brukere kommer inn |
| 2 | 6c Frontend-tester (oppsett) | S | Gjør resten tryggere å endre |
| 3 | 1 Plan-validator | M | Størst synlig kvalitetsløft på AI-planene |
| 4 | 2 Øvelses-ID-er | L | Fundament for 1 (utstyr), progresjon og historikk. Kan starte parallelt med 3 |
| 5 | 4 Planlengde/startdato + 6b | M | Trenger stabile plan-ID-er |
| 6 | 6a Server-innstillinger | S | Enkelt, gjenbruker mønster |
| 7 | 5 Offline-kø | L | Størst, men avhenger ikke av resten |
| 8 | 3B Streaming | M | Etter måling |
| 9 | 6d Render | S | Avhenger av hosting-beslutning |

## Beslutninger jeg trenger fra deg

1. **Punkt 1:** OK med ett ekstra AI-kall når planen bryter regler?
2. **Punkt 2:** Skal vi gjøre den store øvelses-ID-migreringen nå, eller først 1 og 3A?
3. **Punkt 3:** Grenser per bruker (forslag 10/time, 40/dag)?
4. **Punkt 4:** Én aktiv plan om gangen?
5. **Punkt 5:** PWA først eller også native (Capacitor)?
6. **Punkt 6d:** Betalt Render, eller gratis med pinging?

## Ikke gjort / forbehold

- **Streaming av AI-svar (3B):** ikke bygget. Fremdriftstekst under ventingen og latens-logging er på plass;
  streaming venter til vi har målt hvor mye tid modellene faktisk bruker.
- **Render:** kan ikke løses i kode. Se DEPLOY.md (betalt instans er den eneste garanterte løsningen).
- **Øvelses-migrering (2):** ingen engangsjobb, fordi gruppering slår opp navn ved lesing; gamle økter får
  `exerciseId` bare hvis de skrives på nytt.
- **Utstyrssjekken** leser nå utstyr fra fritekst i forespørselen («har bare kettlebells», «ingen kabelmaskin», «kun
  manualer»; `EquipmentNotes`, 2026-09-30). Fortsatt ikke dekket: fritekst i selve profilen (skader o.l.) og notater i
  treningsminnet.
- **Offline** er testet som logikk (kø, idempotens, cache) men ikke prøvd på ekte telefon med flymodus.
