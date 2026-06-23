# Weather-agent — prosjektkontekst / handover

> Denne fila er en komplett oppsummering av prosjektet så langt, ment for å
> fortsette arbeidet på en annen maskin (Windows-desktop). Den fungerer også
> som oppstarts-kontekst for et nytt Claude Code-vindu: be Claude lese
> `docs/HANDOFF.md` først.

Sist oppdatert: 2026-06-23 · Gren: `dev` · Siste commit: `ce9c123`

---

## 1. Hva appen skal være

En nettside/chatbot som finner **hvor i Norge det er finest turvær**.
Bruker skal kunne skrive f.eks. *«hvor i Møre og Romsdal blir det best vær i
helga»* og få beste sted (varmest + minst regn). Skal etter hvert bli en
**tur-assistent** som matcher beste værområde med konkrete turer.

Dette er både et **CV-prosjekt** og noe som faktisk skal brukes.

**Arbeidsstil:** bygges sakte, bottom-up, én testbar modul av gangen, slik at
hele flyten er til å forstå. UI kommer til slutt.

---

## 2. Stack og hvordan kjøre

- **Java 23+** og **Maven** (prosjektet er Spring Boot 3.5, pakke `no.weatheragent`).
- React-frontend kommer senere.

```bash
mvn test              # kjører alle enhetstester (offline, raske)
mvn spring-boot:run   # starter appen; DemoRunner kjører hele kjeden mot live API-er
```

`DemoRunner` er midlertidig stillas som skriver ut resultatet ved oppstart.
Det fjernes når vi får et REST-lag. (Appen starter også Tomcat på :8080, men
det er ingen endepunkter ennå.)

> MET krever en identifiserende User-Agent. Den ligger i
> `src/main/resources/application.properties` (`met.user-agent`) — bytt
> e-posten til din egen når prosjektet publiseres.

---

## 3. Dataflyt (hele bildet)

```
"hvor i Møre og Romsdal blir det best vær i helga"
   │
   ▼
[TOLKNING]   region/fylke + datoer + intensjon        ← IKKE bygget ennå (lokal LLM, se §6)
   │
   ▼
[DATA]       Overpass/OSM: alle navngitte topper i fylket   ✅
   │            → CandidateSelector: rutenett-klynging → få, spredte kandidater
   ▼
[VÆR]        MET LocationForecast per koordinat        ✅
   │
   ▼
[SCORING]    varmt + tørt + lite vind, kun dagtid → rangering   ✅
   │
   ▼
Svar: "Finest vær: <sted> (X°C, Y mm regn)"
```

---

## 4. Hva er bygget (med filer)

Alt følger samme mønster: **record** (data) · **ren parser** (testbar uten nett)
· **tynn klient** (HTTP) · **ren logikk** (testbar).

| Modul | Filer | Status |
|---|---|---|
| Vær (MET) | `weather/WeatherPoint`, `weather/Forecast`, `weather/MetForecastParser`, `weather/MetWeatherClient` | ✅ |
| Geocoding (Open-Meteo) | `geo/Location`, `geo/GeocodingResponseParser`, `geo/OpenMeteoGeocodingClient` | ✅ |
| Kuratert region-liste | `region/Region`, `region/RegionRegistry`, `resources/regions.json` | ✅ |
| Scoring/rangering | `ranking/DayWeather`, `ranking/WeatherScorer`, `ranking/RankedPlace`, `ranking/BestWeatherFinder` | ✅ |
| **Turdata (OSM/Overpass)** | `hiking/Peak`, `hiking/OverpassPeakParser`, `hiking/OverpassClient`, `hiking/CandidateSelector` | ✅ |
| Demo-stillas | `DemoRunner` | midlertidig |

`BestWeatherFinder.rank(List<Location>, LocalDate)` er generisk — både den
kuraterte lista og Overpass-kandidatene mater inn i den.

21 enhetstester, alle grønne. De rene parserne/utvelgerne testes mot faste
JSON-strenger, uten nettverk.

---

## 5. Datakilder (research-resultat)

| Kilde | Hva | Åpent? | Bruk |
|---|---|---|---|
| **MET LocationForecast 2.0** | Vær per koordinat | ✅ (krever User-Agent) | I bruk |
| **Open-Meteo geocoding** | Stedsnavn → koordinat | ✅ ingen nøkkel | I bruk |
| **OpenStreetMap / Overpass** | Navngitte fjelltopper, alle fylker | ✅ ingen nøkkel | I bruk |
| Kartverket/GeoNorge **Turrutebasen** | Offisielle merkede ruter + POI | ✅ åpent WFS/GPX | Kandidat til senere |
| **Naturbase** (nasjonalparker, 48) | Verneområder/parker | 🟡 felles-API krever avtale; grenser åpne via GeoNorge | Senere |
| DNT **Nasjonal Turbase** (UT.no) | Kuraterte turer | 🔴 åpen tilgang STENGT | **Ikke bruk** |

**Overpass-spørringen** (virker for hvilket som helst fylke — bytt navnet):

```overpassql
[out:json][timeout:90];
area["name"="Møre og Romsdal"]["admin_level"="4"]->.fylke;
node(area.fylke)["natural"="peak"]["name"];
out;
```

Datakvalitet for Møre og Romsdal: **1590 navngitte topper, 100 % med
koordinat, 93 % med høyde**, og verdiene er korrekte (Slogen 1564, Romsdalshorn
1550 osv. — ingen hallusinering).

---

## 6. Viktigste arkitektur-beslutning: data > LLM

**Turområdene kommer fra et register (OSM), ALDRI fra LLM-ens hukommelse.**
Små lokale modeller (og til og med store) hallusinerer norske stedsnavn og
spesielt koordinater. Derfor:

- **Fakta (områder + koordinater):** OSM/Overpass. Pålitelig.
- **LLM brukes KUN** til å tolke fritekst-spørsmålet (fylke + dato + intensjon).
  Det er en lett jobb som selv en liten modell klarer.

### Lokal LLM-plan (utsatt til Windows-desktopen)

- Maskiner: MacBook (M3 Pro, 18 GB) + **Windows-desktop (Ryzen 7 9800X3D, 32 GB,
  Radeon RX 9070 XT 16 GB)** — det er denne handover-fila er til.
- På AMD/Windows: bruk **LM Studio (Vulkan-backend)** som trygg start, evt.
  **Ollama** (ROCm). Begge eksponerer et **OpenAI-kompatibelt HTTP-endepunkt**
  (`localhost:1234` / `localhost:11434`).
- Modeller: for *språkforståelse* holder en liten modell (Llama 3.x 8B, Qwen
  2.5 7B, Gemma 2 9B). Det fins også norsk-spesifikke modeller (NB-Llama / NorwAI).
- **Design bak et grensesnitt** (`QueryInterpreter`): regelbasert implementasjon
  først, lokal LLM som drop-in senere — lokal vs. sky blir bare bytte av base-URL.
  Fra Java kalles endepunktet med samme `RestClient`-mønster som MET/Open-Meteo.

---

## 7. Kjent scoring-nuanse

`WeatherScorer` scorer på temperatur (+ tørt + lite vind, kun dagtid 08–20).
Temperatur faller ~0,6 °C per 100 m, så **ren temperatur-scoring favoriserer
lave/kystnære turer** framfor høye fjell. Fjelltopp-kandidater (>800 moh) ga
8–10 °C, mens den gamle by-lista ga ~20 °C — fysisk korrekt.

Justeres senere: vekte høyde, eller la bruker velge «fjelltur» vs «lavtur».
Vektene ligger som konstanter i `WeatherScorer` (`PRECIP_PENALTY`, `WIND_PENALTY`)
og rutenett-/høyde-parametre i `DemoRunner` (`CELL_DEGREES`, `MIN_ELEVATION_M`).

---

## 8. Neste steg

Rekkefølge (bottom-up, UI til slutt):

1. **REST-endepunkt** — `GET /turvaer?fylke=...&dag=...` som returnerer
   rangeringen som JSON. Gjør appen brukbar fra nettleser/curl. *Anbefalt neste.*
2. **Tolkning av fritekst** — `QueryInterpreter`-grensesnitt, regelbasert først,
   lokal LLM på desktopen etterpå.
3. **React-frontend** — chatbot-UI mot REST-laget.
4. Senere: Turrutebasen (merkede ruter), nasjonalparker, høyde-vekting i scoring,
   tur-matching.

---

## 9. Git

- Hovedgren for PR-er: `main`. Jobbes på: `dev`.
- `target/` og `.idea/` er untracket (se `.gitignore`).
- Hemmeligheter/lokal config: legg i `application-local.properties` (gitignorert).
