# Mobil (iPhone først, Android senere) med Capacitor

Appen pakkes som native app med **Capacitor 8**: samme React-kodebase kjører i web, iOS og Android. Capacitor legger den
bygde web-appen (lokale filer) inn i et native skall (WKWebView på iPhone), og appen snakker med backend-API-et på Render over
HTTPS. Backenden er den samme som for nettsiden.

## Status (iPhone)
- `frontend/ios/` er et ferdig Xcode-prosjekt (Swift Package Manager, ingen CocoaPods). Bundle-ID `no.weatheragent.turvaer`,
  visningsnavn «Turvær», **kun iPhone, kun stående**, iOS 15+. Ikon og oppstartsbilde er laget av `public/app-icon.svg`.
- Web-delen bygges i egen modus (`--mode native`, se under), og kommer ut i `frontend/dist-native/` (ikke i Spring sin
  static-mappe). `frontend/.env.native` peker appen mot `https://weather-agent-t801.onrender.com`.
- Backenden tillater `capacitor://localhost` (CORS), sjekket mot produksjon 2026-09-30.
- **Kompilering** kontrolleres av GitHub Actions (`.github/workflows/ios.yml`, jobben `build`, macOS-maskin, uten signering).
- **Ikke gjort ennå:** kjørt på en ekte iPhone eller simulator, signert bygg, TestFlight, App Store. Det krever Apple-konto (se under).

## Bygge og kjøre
Fra `frontend/`:

```bash
npm run ios:sync    # bygger web-appen i native modus og kopierer den inn i ios/-prosjektet
npm run ios:open    # åpner Xcode (kun Mac) -> velg en iPhone/simulator -> Run
```

Etter endringer i frontend: kjør `npm run ios:sync` på nytt. Trenger du `VITE_SUPABASE_URL`/`VITE_SUPABASE_ANON_KEY` lokalt, står
de i `frontend/.env` som vanlig (vite leser `.env` også i native modus).

## Uten Mac
Xcode finnes bare på Mac, men GitHub Actions har Mac-maskiner (gratis for offentlige repoer):
- `build`-jobben kompilerer prosjektet ved hver endring i `frontend/ios/**`, Capacitor-oppsettet eller `ios.yml` (push til `dev`).
- `testflight`-jobben bygger, signerer og laster opp til TestFlight. Den startes manuelt (Actions → iOS → Run workflow → «upload»)
  og finnes i menyen først når `ios.yml` ligger i `main`. **Ikke prøvd ennå** (krever Apple-kontoen under).
- Repo-variabler (Settings → Secrets and variables → Actions → Variables): `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`.
- Hemmeligheter: `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (innholdet i .p8-filen).

## Det du må gjøre (Apple)
1. **Apple Developer Program** (ca. 99 USD/år): developer.apple.com/programs. Uten den kan appen verken lastes opp eller
   installeres på en telefon utenom Xcode med 7-dagers prøvesignering.
2. **App Store Connect → Apps → «+» → New App**: iOS, navn «Turvær», språk norsk, bundle-ID `no.weatheragent.turvaer`
   (registreres under Certificates, Identifiers & Profiles først, eller velges direkte hvis den finnes), en SKU du velger.
   Bundle-ID kan ikke endres etterpå, så bestem den før dette steget.
3. **App Store Connect → Users and Access → Integrations → App Store Connect API**: lag en nøkkel (rolle App Manager), noter
   Key ID og Issuer ID, last ned .p8-filen (kan bare lastes ned én gang). Legg de fire verdiene inn som hemmeligheter.
4. Start `testflight`-jobben. Bygget dukker opp i TestFlight etter noen minutter; installer via TestFlight-appen på iPhonen.

## Til App Store-innsending
- **Personvern-URL** (påkrevd): `https://weather-agent-t801.onrender.com/#personvern` åpner erklæringen direkte. Teksten er fortsatt et
  UTKAST: les den, sett `REVIEWED = true` i `PrivacyView.jsx` og `VITE_CONTACT_EMAIL`, før innsending.
- **Kontosletting i appen** er påkrevd av Apple og finnes (Konto → «Slett kontoen min»).
- **Demo-konto til App Review**: nesten alt ligger bak innlogging, og Apple må kunne logge inn. Lag en bekreftet testbruker i
  Supabase og legg e-post/passord i «App Review Information». (Samme konto kan brukes til `e2e_login.py`.)
- **Privacy Nutrition Label**: e-post, trening og vekt (helse og form), matlogg og brukerinnhold, koblet til brukeren, ikke brukt
  til sporing. Ingen tredjeparts-analyse eller reklame. Appen bruker kun standard HTTPS (`ITSAppUsesNonExemptEncryption = false`).
- Skjermbilder (6,9" og 6,5" iPhone), beskrivelse, støtte-URL, aldersgrense.
- **Sign in with Apple** trengs ikke: appen har bare egen e-post/passord-innlogging, ingen «logg inn med Google/Facebook».
- Risiko: Apple avviser noen ganger apper som bare er en nettside i en ramme (retningslinje 4.2). Appen har mye egen
  funksjonalitet (trening, kosthold, kart), men native ekstra (kameraskanner, haptikk, varsler) styrker saken.

## Kjente begrensninger i iPhone-appen
- **Strekkodeskanneren** virker ikke i WKWebView (ingen `BarcodeDetector`); der skrives koden inn for hånd. Neste steg er en
  polyfill (zxing) eller en native plugin. Da må `NSCameraUsageDescription` også legges i `Info.plist`.
- **E-postbekreftelse:** lenken i bekreftelsesmailen åpnes i Safari (Supabase Site URL), ikke i appen. Brukeren bekrefter og
  logger deretter inn i appen. Dyplenker (universal links) kan komme senere. Det finnes heller ingen «glemt passord»-flyt, verken
  på web eller i appen.
- **Render gratis** sover og bruker ca. 1 minutt på å våkne: dårlig førsteinntrykk i en App Store-app. Bruk betalt instans før lansering.
- Ingen HealthKit, push-varsler eller sanntidssporing ennå.
- Kartfliser og AI-svar krever nett. Treningsøkter fungerer offline (kø + lokal cache), men er ikke prøvd på ekte telefon.

## Android (senere)
`npx cap add android`, så `npm run ios:sync`-tilsvarende (`vite build --mode native && cap sync android`), Android Studio. Capacitor 6+
bruker `https://localhost` som opphav på Android; det står allerede i CORS-standarden (`app.cors.allowed-origins`,
overstyres med `APP_CORS_ALLOWED_ORIGINS` i produksjon: behold `capacitor://localhost` og `https://localhost` der).

## Viktig
- **API-base:** relative `/api`-kall finnes ikke i appen; `VITE_API_BASE` (satt i `.env.native`) gjør at alle kall går til Render.
- **Safe-area:** layouten bruker `env(safe-area-inset-*)` og `viewport-fit=cover`.
- **Ikoner/splash:** kildebildene er `public/app-icon.svg` og `ios/App/App/Assets.xcassets/`. Bytt gjerne til et eget ikon senere.
