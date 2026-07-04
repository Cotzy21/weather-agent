# DEPLOY — sjekkliste før første deploy

> Rekkefølgen er bevisst: alt i «Må gjøres» bør være grønt før appen får en
> offentlig URL. Resten kan tas i dagene etterpå.

## 1. Må gjøres FØR deploy

### Hemmeligheter og miljøvariabler (prod)
- [ ] `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` — Supabase Postgres
      (bruk connection pooler-URL-en for serverless/få tilkoblinger).
- [ ] `SUPABASE_JWT_SECRET` **eller** `SUPABASE_JWKS_URI` — uten disse gir
      alle innloggede endepunkter 401. Sjekk oppstartsloggen:
      `Supabase JWT-validering konfigurert: HS256=…, JWKS=…`.
- [ ] LLM-nøkler (se `application.properties` for navnene) — værsøk-tolkning
      og AI-forslag trenger dem.
- [ ] `MET_USER_AGENT` (met.user-agent): MET **krever** identifiserende
      User-Agent med ekte kontaktinfo — sett appnavn + e-post/URL, ikke
      en placeholder.
- [ ] `APP_CORS_ALLOWED_ORIGINS` — sett til prod-URL-en(e). IKKE la
      localhost-listen stå alene i prod (mobil-appene trenger fortsatt
      capacitor://localhost m.fl. når de kommer).
- [ ] `frontend/.env` ved bygging: `VITE_SUPABASE_URL` + `VITE_SUPABASE_ANON_KEY`
      (anon key er offentlig og trygg i frontend). `VITE_API_BASE` bare hvis
      frontend serveres et annet sted enn backend.
- [ ] Dobbeltsjekk at ingen hemmeligheter ligger i git
      (`git log -p | grep -i secret` e.l. — `.env`/`application-local.properties`
      er gitignored, men verifiser).

### Bygg og kjøring
- [ ] `cd frontend && npm run build` → legger static i Spring
      (`src/main/resources/static/`) → `mvn package` → ÉN kjørbar jar.
- [ ] Velg host (Railway/Render/Fly.io er enkle for én jar + de har
      Postgres-nærhet til Supabase). Krav: Java 21+-runtime, HTTPS følger med.
- [ ] Flyway kjører V1–V9 automatisk mot prod-DB ved første oppstart —
      verifiser i loggen at alle migrasjonene gikk.
- [ ] Kjør HELE appen lokalt med prod-lignende env først (kjent hull:
      mye er verifisert med mocks, ikke ende-til-ende mot ekte DB/auth).

### Lisenser og attribusjon (viktig — appen skal være kommersiell etter hvert)
- [ ] **Open-Meteo**: brukes til geocoding, vær-fallback OG høydeprofil.
      Gratis-nivået er KUN ikke-kommersielt. Før betalende brukere: kjøp
      API-plan (rimelig) eller bytt kilde. For gratis lansering: OK, men
      legg synlig attribusjon («Weather data by Open-Meteo.com», CC-BY 4.0).
- [ ] **MET**: CC-BY 4.0 — legg synlig attribusjon i UI («Værdata fra
      Meteorologisk institutt») i tillegg til User-Agent-kravet.
- [ ] **OpenStreetMap/Overpass**: kart-attribusjon finnes allerede i
      Leaflet-hjørnet ✓. De OFFENTLIGE Overpass-instansene er for moderat
      bruk — ved vekst: egen Overpass-instans eller betalt tilbyder.
- [ ] **Matvaretabellen**: NLOD, attribusjon vises i UI ✓.

### Sikkerhet
- [ ] **Rate limiting**: `/api/turvaer` og `/api/rute` er uautentiserte og
      utløser eksterne API-kall + LLM-kall — uten takling kan én person
      tømme kvotene/lommeboka. Legg på f.eks. bucket4j per IP, eller krev
      innlogging for søk ved lansering.
- [ ] **Supabase RLS**: backend går rett på Postgres, men anon-nøkkelen gir
      OGSÅ tilgang til Supabase sitt REST-API (PostgREST). Slå på RLS på
      alle tabeller uten policies (default deny) så ingen kan lese
      `meal_entries` m.m. direkte via Supabase-API-et.
- [ ] Ikke eksponer Spring Actuator offentlig (er ikke avhengighet i dag —
      hold det slik, eller lås ned hvis det legges til for helsesjekk).
- [ ] Sjekk at feilmeldinger ikke lekker interndetaljer (ApiExceptionHandler
      dekker eksterne tjenester ✓).

### Juridisk (helsedata = ekstra ansvar)
- [ ] **Personvernerklæring**: appen lagrer helseopplysninger (vekt, alder,
      kjønn, kalorimål, treningsdata) → GDPR art. 9. Minimum: hva lagres,
      hvor (Supabase, region?), hvordan slettes det, behandlingsgrunnlag
      (samtykke). Velg EU-region i Supabase hvis ikke alt gjort.
- [ ] Slette-min-konto-funksjon (GDPR retten til sletting) — finnes ikke
      ennå; minimum en e-postadresse for slettehenvendelser ved lansering.
- [ ] «Ikke medisinske råd»-disclaimere finnes i Restitusjon ✓ — vurder
      samme i kosthold (kalorimål).

## 2. Bør gjøres like etter

- [ ] Enkel overvåkning: uptime-sjekk (UptimeRobot o.l.) + le av loggene
      første dagene. Vurder `spring-boot-starter-actuator` med kun
      `/actuator/health` åpen for hosten sin helsesjekk.
- [ ] Backup: Supabase har PITR/backups på betalte planer — sjekk hva
      gratisplanen gir og om det holder.
- [ ] Cache er in-memory og tømmes ved restart/deploy (peaks/trails/foods) —
      helt OK for én instans; Matvaretabellen (~14 MB) lastes på nytt ved
      første kosthold-søk etter restart. Ved flere instanser senere: Redis.
- [ ] LLM-kost per værsøk: mål faktisk forbruk første uka og sett budsjett/
      alarm hos leverandøren.
- [ ] Overpass-speilene: følg med på 429-rater i loggen; vurder egen instans.

## 3. Kan vente (fra planen)

- Klokke-integrasjoner (Apple Health/Garmin/Strava/Whoop) — husk
  sikkerhetsprinsippet: minst mulig lagring, per-bruker kryptering.
- Prismodell (abonnement vs bruksbasert) — regnestykket bør bruke målt
  LLM-/API-kost fra punktet over.
- Backend-i18n (Accept-Language) for råd/feilmeldinger.
- Strekkodeskanning, offentlig deling av egne matvarer.
