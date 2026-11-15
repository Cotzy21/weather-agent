import { useI18n } from './i18n.jsx'

// Personvernerklæring. Innholdet er skrevet ut fra hva appen faktisk lagrer og hvilke tjenester den kaller
// (se DEPLOY.md og SECURITY.md), men er et UTKAST til eieren skal lese gjennom: sett REVIEWED = true når det er gjort.
// Kontaktadressen kommer fra VITE_CONTACT_EMAIL (frontend/.env); uten den vises ingen adresse.
const REVIEWED = false
const CONTACT = import.meta.env.VITE_CONTACT_EMAIL || ''

const CONTENT = {
  nb: {
    title: 'Personvernerklæring',
    updated: 'Sist oppdatert 15. november 2026',
    draft: 'Utkast: denne teksten gjennomgås av eieren før offentlig lansering.',
    sections: [
      ['Hvem er ansvarlig',
        ['Weather Agent drives av Oskar Oliwier Kotyla, som er behandlingsansvarlig for opplysningene du legger inn.',
          CONTACT ? `Spørsmål om personvern: ${CONTACT}` : 'Du kan slette kontoen og alle dataene dine selv i appen (Min konto).']],
      ['Hva vi lagrer',
        ['Uten innlogging lagrer vi ingen kontodata om deg: værsøk og ruteplanlegging kan brukes uten konto.',
          'Med konto lagrer vi e-postadressen din og passordet ditt (som en hash, hos innloggingstjenesten), og det du selv legger inn: treningsøkter, treningsplaner og treningsprofil (erfaring, mål, utstyr, skader og begrensninger), vekt, kalorimål og kroppsdata som alder, kjønn, vekt, høyde og aktivitetsnivå, måltider og matdagbok, kostpreferanser og allergier, vaner (for eksempel søvn og koffein), lagrede ruter, egne matvarer og måltider, og innstillinger.',
          'Mye av dette er helseopplysninger, som er en særlig kategori personopplysninger. Vi bruker dem bare til å gi deg funksjonene i appen, som progresjon, råd og treningsplaner.']],
      ['Rettslig grunnlag',
        ['Samtykke til helselagring og samtykke til trenings-AI er to separate valg (GDPR artikkel 6 nr. 1 bokstav a og artikkel 9 nr. 2 bokstav a for helseopplysninger). Du kan opprette konto og bruke vær og ruter uten disse samtykkene. Under Konto kan du trekke samtykket til helselagring tilbake. Da slettes helsedataene fra den aktive databasen, mens innlogging og lagrede ruter beholdes. Eksport og kontosletting er fortsatt tilgjengelig. Trenings-AI krever et eget aktivt samtykke. Du kan trekke tilbake dette samtykket under Konto, uten å miste treningsloggen. Forespørsler som allerede er sendt til AI-tjenesten kan ikke trekkes tilbake fra appen.']],
      ['Hvem får opplysningene',
        ['Vi selger ikke opplysningene dine og viser ingen annonser. Disse tjenestene behandler data på våre vegne:',
          '• Supabase: database og innlogging.',
          '• Render: kjører tjenesten (serveren).',
          '• En språkmodell-leverandør (i dag en OpenAI-kompatibel tjeneste): teksten du skriver i værsøket og til AI-treningsassistenten, sammen med den treningsprofilen og historikken som trengs for å lage planen, sendes dit for å lage svaret. E-postadressen din legges ikke automatisk til. Garmin-økter og eldre økter med ukjent opphav utelates fra automatisk AI-historikk og avledet treningsstatistikk. Tekst du selv skriver til assistenten sendes fortsatt; ikke lim inn nøkler eller opplysninger du ikke vil dele.',
          'Værdata hentes fra Meteorologisk institutt (MET) og Open-Meteo, stedsnavn og terreng fra OpenStreetMap (Overpass), ruter fra OpenRouteService, og næringsinnhold fra Matvaretabellen og Open Food Facts (strekkoder du skanner slås opp der). Disse får stedsnavn, koordinater eller søkeord, men ingenting som knytter dem til deg. Kartfliser lastes direkte fra kartleverandørene (OpenStreetMap, OpenTopoMap og Esri), som dermed ser IP-adressen din.',
          'Hvis du velger å hente varselet direkte fra Open-Meteo på forsiden, sender nettleseren koordinatene for byen du valgte. Open-Meteo ser IP-adressen din. Kontoopplysninger og innlogging sendes ikke.',
          'Matvarer du velger å dele offentlig, kan alle innloggede brukere se.']],
      ['Garmin-import',
        ['Nye Garmin-importer er satt på pause. Tidligere importer kunne sende CSV-data, tolkede økter og målinger til serveren. Ved direkte import brukte serveren nøkkelen midlertidig. Tidligere lagrede data ligger fortsatt på kontoen uten ende-til-ende-kryptering. Du kan slette målinger under Restitusjon og importerte økter i treningsloggen. Et privat iOS-utviklerbygg har et separat lokalt Garmin-lager for eierens egne data. Nøkkelen sendes direkte til Garmin ved manuell henting. Lokale data og eventuell lagret nøkkel kan slettes fra Garmin-lageret.']],
      ['Lokal lagring i nettleseren',
        ['Appen bruker nettleserens lokale lagring til innlogging, språk, tema, en hurtigbuffer og økter som venter på nett. Disse lokale treningsdataene har foreløpig ikke egen kryptering i appen. Eksisterende serverdata er heller ikke ende-til-ende-kryptert. Vi bruker ingen sporings- eller markedsføringskapsler og ingen analyseverktøy.']],
      ['Hvor lenge vi lagrer',
        ['Kontodata beholdes til du sletter dem eller kontoen. Ved kontosletting fjernes appdataene i én samlet operasjon, og kontoen sperres for nye forespørsler. Innloggingen hos Supabase slettes separat. Hvis dette feiler, får du beskjed, og tjenesten prøver igjen automatisk. Leverandørenes faktiske oppbevaringstid for sikkerhetskopier må avklares før denne erklæringen ferdigstilles.',
          'En sperre med konto-ID, slettetidspunkt og status for sletting av innloggingen beholdes for å avvise gamle innloggingstokens. Den inneholder ingen e-postadresse, nøkkel eller helsedata.']],
      ['Dine rettigheter',
        ['Du har rett til innsyn, retting, sletting, begrensning av behandlingen, dataportabilitet og til å trekke samtykket tilbake. Det meste kan du gjøre selv i appen: se og rette dataene dine, og slette kontoen. Under Min konto kan du laste ned en JSON-kopi av appdataene som er lagret på serveren. Eksportfilen er ikke kryptert og må oppbevares trygt. Den omfatter ikke leverandørenes egne driftslogger eller data som bare finnes på andre enheter.',
          'Du kan klage til Datatilsynet (datatilsynet.no) hvis du mener vi behandler opplysningene dine i strid med regelverket.']],
      ['Ikke medisinsk rådgivning',
        ['Råd om trening, kosthold og restitusjon er generelle veiledninger og ikke medisinske råd. Snakk med lege eller annet helsepersonell ved sykdom, skade eller spesielle behov.']],
    ],
    back: '← Tilbake',
  },
  en: {
    title: 'Privacy policy',
    updated: 'Last updated 15 November 2026',
    draft: 'Draft: the owner will review this text before the public launch.',
    sections: [
      ['Who is responsible',
        ['Weather Agent is run by Oskar Oliwier Kotyla, who is the data controller for the information you enter.',
          CONTACT ? `Privacy questions: ${CONTACT}` : 'You can delete your account and all your data yourself in the app (My account).']],
      ['What we store',
        ['Without logging in we store no account data about you: weather search and route planning work without an account.',
          'With an account we store your email address and your password (as a hash, at the login service), and what you enter yourself: workouts, training plans and training profile (experience, goals, equipment, injuries and limitations), weight, calorie goal and body data such as age, sex, weight, height and activity level, meals and food diary, diet preferences and allergies, habits (for example sleep and caffeine), saved routes, your own foods and meals, and settings.',
          'Much of this is health data, which is a special category of personal data. We only use it to provide the features of the app, such as progression, advice and training plans.']],
      ['Legal basis',
        ['Health data storage and Training AI have separate consent choices (GDPR Article 6(1)(a) and Article 9(2)(a) for health information). You can create an account and use weather and routes without either permission. You can withdraw your storage consent under Account. This removes health data from the active database and keeps your login and saved routes. Export and account deletion remain available. Training AI requires a separate explicit opt-in. You can withdraw this permission under Account and keep your training log. The app cannot recall requests already sent to the AI service.']],
      ['Who receives the information',
        ['We do not sell your information and show no ads. These services process data on our behalf:',
          '• Supabase: database and login.',
          '• Render: runs the service (the server).',
          '• A language-model provider (today an OpenAI-compatible service): the text you write in weather search and to the AI training assistant, together with the training profile and history needed to build the plan, is sent there to produce the answer. Your email address is not added automatically. Garmin workouts and older workouts with unknown provenance are excluded from automatic AI history and derived training statistics. Text you write to the assistant is still sent; do not paste keys or information you do not want to share.',
          'Weather data comes from the Norwegian Meteorological Institute (MET) and Open-Meteo, place names and terrain from OpenStreetMap (Overpass), routes from OpenRouteService, and nutrition data from Matvaretabellen and Open Food Facts (barcodes you scan are looked up there). They receive place names, coordinates or search terms, but nothing that links them to you. Map tiles are loaded directly from the map providers (OpenStreetMap, OpenTopoMap and Esri), which therefore see your IP address.',
          'If you choose to fetch the forecast directly from Open-Meteo on the landing page, your browser sends the coordinates of the selected city. Open-Meteo sees your IP address. Account details and credentials are not sent.',
          'Foods you choose to share publicly can be seen by all logged-in users.']],
      ['Garmin import',
        ['New Garmin imports are paused. Previous imports could send CSV data, parsed workouts and measurements to the server. For direct imports, the server used the key temporarily. Previously saved data remains in your account without end-to-end encryption. You can delete measurements in Recovery and imported workouts in the training log. A private iOS development build provides a separate local Garmin vault for the owner’s own data. The key is sent directly to Garmin on manual refresh. Local data and any remembered key can be erased in the vault.']],
      ['Local storage in the browser',
        ['The app uses the browser\'s local storage for login, language, theme, a cache and workouts waiting for a connection. These local training records do not yet have app-level encryption. Existing server data is not end-to-end encrypted either. We use no tracking or marketing cookies and no analytics tools.']],
      ['How long we keep it',
        ['Account data is kept until you delete it or the account. Account deletion removes app data in one operation and blocks new requests for that account. The Supabase login is removed separately. If that fails, you are told and the service retries automatically. The providers’ actual backup retention periods must be confirmed before this policy is finalized.',
          'A blocking record containing the account ID, deletion time and login-removal status is retained to reject old login tokens. It contains no email address, key or health data.']],
      ['Your rights',
        ['You have the right of access, rectification, erasure, restriction of processing, data portability and to withdraw your consent. You can do most of this yourself in the app: view and correct your data, and delete the account. Under My account you can download a JSON copy of app data stored on the server. The export file is not encrypted and must be stored safely. It does not include providers’ own operational logs or data held only on other devices.',
          'You can complain to the Norwegian Data Protection Authority (datatilsynet.no) if you believe we process your information in breach of the rules.']],
      ['Not medical advice',
        ['Advice on training, nutrition and recovery is general guidance and not medical advice. Talk to a doctor or other health professional in case of illness, injury or special needs.']],
    ],
    back: '← Back',
  },
}

export default function PrivacyView({ onBack }) {
  const { lang } = useI18n()
  const c = CONTENT[lang] ?? CONTENT.en

  return (
    <article className="privacy">
      {onBack && <button className="link-btn" onClick={onBack}>{c.back}</button>}
      <h1 className="detail-title">{c.title}</h1>
      <p className="muted">{c.updated}</p>
      {!REVIEWED && <p className="muted privacy-draft" role="note">{c.draft}</p>}
      {c.sections.map(([heading, paragraphs]) => (
        <section key={heading}>
          <h3>{heading}</h3>
          {paragraphs.map((p) => <p key={p}>{p}</p>)}
        </section>
      ))}
    </article>
  )
}
