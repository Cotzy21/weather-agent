# Mobil (iOS + Android) med Capacitor

Appen pakkes som native app med **Capacitor** – samme React-kodebase kjører i web,
iOS og Android. Capacitor legger den bygde web-appen inn i et native skall og lar
den snakke med backend-API-et over nett.

## Forutsetninger
- **Android:** Android Studio (+ SDK).
- **iOS:** en **Mac** med Xcode (iOS kan ikke bygges på Windows).
- Node (har du).

## Engangs-oppsett (legg til plattformene)
Fra `frontend/`:

```bash
# 1) Bygg web-appen inn i Spring sin static-mappe (webDir i capacitor.config.json).
#    Sett API-base til den utplasserte backenden (mobil kan ikke bruke proxy):
#    Windows PowerShell:  $env:VITE_API_BASE="https://api.din-app.no"; npm run build
#    macOS/Linux:         VITE_API_BASE="https://api.din-app.no" npm run build
npm run build

# 2) Legg til plattformene (lager frontend/android og frontend/ios)
npx cap add android
npx cap add ios        # kun på Mac

# 3) Kopier web-assets + plugins inn i native prosjektene
npx cap sync
```

## Daglig flyt (etter endringer i frontend)
```bash
npm run build      # husk VITE_API_BASE for mobil-bygg
npx cap sync
npx cap open android   # åpner Android Studio -> Run
npx cap open ios       # åpner Xcode (Mac) -> Run
```

## Viktig
- **API-base:** mobil-appen bruker `VITE_API_BASE` (se `.env`), siden relative `/api`-kall
  og Vite-proxy ikke finnes i appen. Backend må derfor være utplassert og nåbar.
- **CORS:** backend tillater opphavene `capacitor://localhost` (iOS) og `http://localhost`
  (Android) – styr med env `APP_CORS_ALLOWED_ORIGINS` i produksjon.
- **Auth:** Supabase-innlogging fungerer uendret fra appen (HTTPS mot Supabase).
- **Safe-area:** layouten respekterer notch/hjemindikator (`env(safe-area-inset-*)`).
- **Ikoner/splash:** generer med `@capacitor/assets` fra et kildebilde når du vil ha
  egne app-ikoner (senere).
