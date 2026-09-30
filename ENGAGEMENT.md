# ENGAGEMENT — plan for vaner, serier og «hekte»-mekanismer

Bygget på forskningsrapporten Oskar limte inn 2026-09-30 (Duolingo, Strava, treningsapper, psykologi, gamblingdesign). Rapporten ligger i
Hindsight som 20 biter: `research/engasjement/00-indeks.md` og `01`–`17` (søk f.eks. «Milkman comeback», «Silverman Barasch»,
«Fase 0 tabell»). Denne filen er planen for HVA av det vi bygger, i hvilken rekkefølge, og hva som allerede fantes.

**Kjernen:** varig retensjon kommer av en *tilgivende* serie, synlig fremgang, små sosiale forpliktelser og få, godt timede påminnelser.
Ikke av spilleautomat-triks. Alt i Fase 0 regnes ut lokalt i nettleseren fra data brukeren allerede har (ingen API-kostnad), og
skal aldri komme i konflikt med appens fokus på restitusjon.

## Prinsipper (gjelder alt vi bygger)
1. **Serien er ukebasert og tilgivende.** Vi teller uker som oppfyller planen, ikke dager. En hviledag er aldri et brudd.
2. **Ett brudd skal ikke koste alt.** Opptjente hvilekort dekker en glipp; ellers kan serien reddes med HANDLING (én økt ekstra uka
   etter), aldri med penger. Vis alltid lengste serie og totalt antall gode uker ved siden av nåværende serie.
3. **Feir det som er ekte.** PR-er og fremgang kommer fra brukerens egne tall. Ingen konstruerte «nesten»-øyeblikk, ingen falsk
   knapphet, ingen nedtellinger som ikke er ekte.
4. **Belønn comeback, ikke skyld.** Tonen etter en glipp er «10 min mobilitet teller», ikke «uglen er skuffet».
5. **Få varsler.** Maks ett proaktivt varsel per dag, roterende tekst, stille modus, generisk tekst på låseskjermen (ingen helsedata).
6. **Personvern som fordel.** Beregning lokalt, full eksport, samtykke for helsedata, ingen tredjeparts-SDK.
7. **Aldri (🔴):** daglig innloggingsbonus, loot-bokser, skyldvarsler, betalbar seriereparasjon, skjulte kostnader, konstruerte nesten-gevinster.

## Det som fantes fra før (kartlagt 2026-09-30)
| Område | Finnes | Sted |
|---|---|---|
| Vane-tracker | 7-dagers rutenett, enhet/mengde, **daglig** serie regnet på serveren | `HabitTracker.jsx`, `habit/HabitService.java` (V9) |
| Program | Startdato + antall uker, ukeoversikt med planlagt/gjort/hvile | `WeekProgram.jsx`, `ProgramBar.jsx`, `SettingsController` (`user_settings`) |
| Økter | JSONB-innhold med sett (reps, kg), type, `clientId` | `training/Workout.java`, V2/V3/V15 |
| Estimert 1RM | Epley + per-øvelse-serier og trender | `exerciseProgress.js` |
| Dagsform | GOD/MIDDELS/LAV fra søvn-vanen | `training/ReadinessService.java` |
| Skaderisiko | ACWR (7 d mot 4 uker), hviledagsforslag etter 3+ dager | `recovery/InjuryRiskAnalyzer.java`, `RecoveryAdvisor.java` |
| Dashboard | Kort-register, tilpasning, lagring per konto | `dashboardLayout.js`, `Dashboard.jsx` |
| Innstillinger | Nøkkel/verdi-JSON per bruker (8000 tegn) | `UserSettingsService` |
| **Mangler** | ukeserie, PR-deteksjon/feiring, kostloggingsserie, varsler/påminnelser, «Året ditt», tur-merker, ligaer |  |

## Rekkefølge og status

Merking: 🟢 etisk/brukeralignet. Status: ✅ ferdig · 🚧 pågår · ⏳ neste · 💤 senere (venter på noe).

| # | Mekanisme | Lokal/server | Status |
|---|---|---|---|
| 0.1 | **Ukeserie** med mål per uke, opptjente hvilekort (1 per 4 gode uker, maks 2), pause (sykdom/ferie), reparasjon ved comeback. Kort «Ukeserie» på Hjem. 🟢 | Lokal regning, mål og pauser i `user_settings` (`streak`) | ✅ (0c720ff) |
| 0.2 | **PR-deteksjon og feiring** etter lagret økt (e1RM- og rep-rekord mot egen historikk), 🏆 på rekordsett i live-økta. 🟢 | Lokal | ✅ |
| 0.3 | Fremgangsvisualisering: kalender-heatmap og «denne måneden mot forrige» (grafer per øvelse finnes) | Lokal | ⏳ |
| 0.4 | Daglig restitusjonsinnsjekk som «dagens vane»: kort med GOD/MIDDELS/LAV + anbefaling (dagsform finnes, mangler innsjekk-flyt) | Lokal | ⏳ |
| 0.5 | Volumvarsel koblet til serien: en uke der ACWR er høy telles som oppfylt («hviluke»); nedtrapping bryter aldri serien | Lokal | ⏳ |
| 0.6 | Vane-tracker med «aldri to på rad» og comeback-melding (i dag daglig serie) | Lokal | ⏳ |
| 0.7 | Kostloggingsserie: «logget minst ett måltid» | Lokal | ⏳ |
| 0.8 | Tur-merker og samlekart fra ruteplanleggeren (forhåndsresearchet liste over toppturer/hytter) | Lokal + data | 💤 (trenger dataliste) |
| 0.9 | Dagens 3 oppdrag | Lokal | 💤 |
| 0.10 | «Året ditt» / måned i tall, delbart bilde | Lokal | 💤 |
| 0.11 | Fresh start-markører (mandag, månedsskifte) | Lokal | ⏳ (følger med 0.1: «ny uke»-tekst) |
| 0.12 | Én smart påminnelse per dag (lokalt varsel) | Lokal | 💤 (trenger native varsler i iPhone-appen, se `frontend/MOBILE.md`) |
| 1.x | Venneliga, felles ukeserie, kudos, felles utfordring, Local Legend for turer, comeback-bonus | Server (kun aggregater) | 💤 (trenger venner/brukere) |
| 2.x | Transparent AI-kvote, opptjente hvilekort, sesongprogram, win-back-e-post | Server | 💤 (kommersiell lansering) |

## Beslutninger for 0.1 (kan endres; skriv til Oskar hvis du vil ha noe annet)
- **Uke** = mandag–søndag, norsk dato. **Teller** alle økttyper (styrke, løping, tur, sykkel …). Det telles **treningsdager** (to økter samme
  dag er én), så serien ikke kan pumpes.
- **Mål** = 3 treningsdager per uke som standard, 1–7 justerbart. Ukens mål påvirker bare uker som ikke er avsluttet ennå; en uke som er
  ferdig vurderes mot målet slik det står nå (enkelt og ærlig; vi lagrer ikke målhistorikk).
- **Hvilekort:** +1 for hver 4. gode uke på rad i opptjening (maks 2 på lager). Dekker en uke som ikke nådde målet: serien fortsetter, men
  uka gir ikke +1. Kan ikke kjøpes.
- **Reparasjon:** uten hvilekort og etter en glipp kan serien reddes ved å nå målet + 1 (f.eks. 4 dager) i uka ETTER glippen. Da fortsetter
  den gamle serien (+1). Nås bare målet, starter en ny serie på 1 og det telles som comeback. Lengste serie huskes uansett.
- **Pause:** brukeren kan pause en uke (sykdom, ferie, skade). En pauset uke verken bryter, øker eller bruker hvilekort. Lagres som uke-start-datoer
  (maks 52).
- **Ingen serie før første økt:** vurderingen starter uka med første loggede økt.

## Beslutninger for 0.2 (rekorder)
- **Rekord** = høyere estimert 1RM (Epley, bare sett på 1–12 reps) enn noen tidligere økt, eller flere reps i en kroppsvektøvelse (0 kg).
  Én rekord per øvelse per økt; «tyngste vekt noensinne» nevnes når det også stemmer.
- **Første gang en øvelse logges er ikke en rekord**, og uten lastet historikk feires ingenting (ingen falske rekorder). Samme øvelse
  på norsk og engelsk er samme øvelse (`exerciseKey`).
- **Live-økta:** det beste avhukede settet som slår rekorden får 🏆 i stedet for settnummeret, med kort vibrasjon første gang.
  **Etter lagring** (live og manuell) vises en feiring med tallene og forrige rekord. Manuelle økter eldre enn 2 dager feires ikke.
- Ikke bygget med vilje: «X reps til rekord»-hint (krever presist mål for hvert sett; kommer hvis Oskar vil ha det) og delbart rekordkort.

## Metrikker (senere, lokalt dev-panel)
Kjerne: D1/D7/D30 med «aktiv» = kjernehandling (økt, innsjekk, måltid). Ukeretensjon (CURR/NURR/RURR i uker). Fordeling av serielengde.
**Signal på om designet er tilgivende nok:** andel brudd som følges av comeback innen 14 dager. **Sikkerhet:** andel hviledager fulgt ved LAV
dagsform; øker serielengde mens hviledager ved rød score går ned, har vi bygget en overtreningsmaskin.

## Åpne spørsmål til Oskar
1. Er 3 treningsdager i uka riktig standardmål? Skal turer og gåing telle like mye som styrkeøkter?
2. Vil du ha varsler (0.12) først når iPhone-appen er på TestFlight, eller også som nettleservarsel i PWA-en?
3. Tur-merker (0.8): vil du selv sette opp lista over toppturer/hytter, eller skal jeg lage et første utkast fra OpenStreetMap?
