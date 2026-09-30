import { useI18n } from './i18n.jsx'

// Personvernerklæring. Innholdet er skrevet ut fra hva appen faktisk lagrer og hvilke tjenester den kaller
// (se DEPLOY.md og SECURITY.md), men er et UTKAST til eieren skal lese gjennom: sett REVIEWED = true når det er gjort.
// Kontaktadressen kommer fra VITE_CONTACT_EMAIL (frontend/.env); uten den vises ingen adresse.
const REVIEWED = false
const CONTACT = import.meta.env.VITE_CONTACT_EMAIL || ''

const CONTENT = {
  nb: {
    title: 'Personvernerklæring',
    updated: 'Sist oppdatert 30. september 2026',
    draft: 'Utkast: denne teksten gjennomgås av eieren før offentlig lansering.',
    sections: [
      ['Hvem er ansvarlig',
        ['Turvær drives av appens eier, som er behandlingsansvarlig for opplysningene du legger inn.',
          CONTACT ? `Spørsmål om personvern: ${CONTACT}` : 'Du kan slette kontoen og alle dataene dine selv i appen (Min konto).']],
      ['Hva vi lagrer',
        ['Uten innlogging lagrer vi ingen kontodata om deg: værsøk og ruteplanlegging kan brukes uten konto.',
          'Med konto lagrer vi e-postadressen din og passordet ditt (som en kryptert hash, hos innloggingstjenesten), og det du selv legger inn: treningsøkter, treningsplaner og treningsprofil (erfaring, mål, utstyr, skader og begrensninger), vekt, kalorimål og kroppsdata som alder, kjønn, vekt, høyde og aktivitetsnivå, måltider og matdagbok, kostpreferanser og allergier, vaner (for eksempel søvn og koffein), lagrede ruter, egne matvarer og måltider, og innstillinger.',
          'Mye av dette er helseopplysninger, som er en særlig kategori personopplysninger. Vi bruker dem bare til å gi deg funksjonene i appen, som progresjon, råd og treningsplaner.']],
      ['Rettslig grunnlag',
        ['Vi behandler opplysningene dine på grunnlag av samtykket ditt (GDPR artikkel 6 nr. 1 bokstav a og artikkel 9 nr. 2 bokstav a). Du samtykker når du oppretter konto, og du kan når som helst trekke samtykket tilbake ved å slette kontoen din.']],
      ['Hvem får opplysningene',
        ['Vi selger ikke opplysningene dine og viser ingen annonser. Disse tjenestene behandler data på våre vegne:',
          '• Supabase: database og innlogging.',
          '• Render: kjører tjenesten (serveren).',
          '• En språkmodell-leverandør (i dag en OpenAI-kompatibel tjeneste): teksten du skriver i værsøket og til AI-treningsassistenten, sammen med den treningsprofilen og historikken som trengs for å lage planen, sendes dit for å lage svaret. E-postadressen din sendes ikke.',
          'Værdata hentes fra Meteorologisk institutt (MET) og Open-Meteo, stedsnavn og terreng fra OpenStreetMap (Overpass), ruter fra OpenRouteService, og næringsinnhold fra Matvaretabellen og Open Food Facts (strekkoder du skanner slås opp der). Disse får stedsnavn, koordinater eller søkeord, men ingenting som knytter dem til deg. Kartfliser lastes direkte fra kartleverandørene (OpenStreetMap, OpenTopoMap og Esri), som dermed ser IP-adressen din.',
          'Matvarer du velger å dele offentlig, kan alle innloggede brukere se.']],
      ['Garmin-import',
        ['Garmin-filer (CSV, FIT og ZIP) leses på enheten din. Bare øktene, øvelsene, settene og vektene sendes til serveren, ikke selve filene. Unike øvelsesnavn (uten vekter og datoer) kan sendes til språkmodellen for å kobles til appens øvelser.']],
      ['Lokal lagring i nettleseren',
        ['Appen bruker nettleserens lokale lagring til innlogging, språk, tema, en hurtigbuffer og økter som venter på nett. Vi bruker ingen sporings- eller markedsføringskapsler og ingen analyseverktøy.']],
      ['Hvor lenge vi lagrer',
        ['Til du sletter kontoen. Sletter du kontoen (Min konto → Slett kontoen min), fjernes alle dataene dine fra databasen umiddelbart, og vi fjerner innloggingen din (e-postadressen) samtidig. Klarer ikke appen det automatisk, får du beskjed om det. Sikkerhetskopier hos leverandørene kan ligge igjen en kort periode etter sletting.']],
      ['Dine rettigheter',
        ['Du har rett til innsyn, retting, sletting, begrensning av behandlingen, dataportabilitet og til å trekke samtykket tilbake. Det meste kan du gjøre selv i appen: se og rette dataene dine, og slette kontoen. For innsyn eller en kopi av dataene, ta kontakt.',
          'Du kan klage til Datatilsynet (datatilsynet.no) hvis du mener vi behandler opplysningene dine i strid med regelverket.']],
      ['Ikke medisinsk rådgivning',
        ['Råd om trening, kosthold og restitusjon er generelle veiledninger og ikke medisinske råd. Snakk med lege eller annet helsepersonell ved sykdom, skade eller spesielle behov.']],
    ],
    back: '← Tilbake',
  },
  en: {
    title: 'Privacy policy',
    updated: 'Last updated 30 September 2026',
    draft: 'Draft: the owner will review this text before the public launch.',
    sections: [
      ['Who is responsible',
        ['Turvær is run by the owner of the app, who is the data controller for the information you enter.',
          CONTACT ? `Privacy questions: ${CONTACT}` : 'You can delete your account and all your data yourself in the app (My account).']],
      ['What we store',
        ['Without logging in we store no account data about you: weather search and route planning work without an account.',
          'With an account we store your email address and your password (as an encrypted hash, at the login service), and what you enter yourself: workouts, training plans and training profile (experience, goals, equipment, injuries and limitations), weight, calorie goal and body data such as age, sex, weight, height and activity level, meals and food diary, diet preferences and allergies, habits (for example sleep and caffeine), saved routes, your own foods and meals, and settings.',
          'Much of this is health data, which is a special category of personal data. We only use it to provide the features of the app, such as progression, advice and training plans.']],
      ['Legal basis',
        ['We process your information based on your consent (GDPR Article 6(1)(a) and Article 9(2)(a)). You consent when you create an account, and you can withdraw your consent at any time by deleting your account.']],
      ['Who receives the information',
        ['We do not sell your information and show no ads. These services process data on our behalf:',
          '• Supabase: database and login.',
          '• Render: runs the service (the server).',
          '• A language-model provider (today an OpenAI-compatible service): the text you write in weather search and to the AI training assistant, together with the training profile and history needed to build the plan, is sent there to produce the answer. Your email address is not sent.',
          'Weather data comes from the Norwegian Meteorological Institute (MET) and Open-Meteo, place names and terrain from OpenStreetMap (Overpass), routes from OpenRouteService, and nutrition data from Matvaretabellen and Open Food Facts (barcodes you scan are looked up there). They receive place names, coordinates or search terms, but nothing that links them to you. Map tiles are loaded directly from the map providers (OpenStreetMap, OpenTopoMap and Esri), which therefore see your IP address.',
          'Foods you choose to share publicly can be seen by all logged-in users.']],
      ['Garmin import',
        ['Garmin files (CSV, FIT and ZIP) are read on your device. Only the workouts, exercises, sets and weights are sent to the server, not the files themselves. Unique exercise names (without weights or dates) may be sent to the language model to match them to the app\'s exercises.']],
      ['Local storage in the browser',
        ['The app uses the browser\'s local storage for login, language, theme, a cache and workouts waiting for a connection. We use no tracking or marketing cookies and no analytics tools.']],
      ['How long we keep it',
        ['Until you delete the account. If you delete your account (My account → Delete my account), all your data is removed from the database immediately, and we remove your login (email address) at the same time. If the app cannot do that automatically, you are told. Backups at the providers may remain for a short period after deletion.']],
      ['Your rights',
        ['You have the right of access, rectification, erasure, restriction of processing, data portability and to withdraw your consent. You can do most of this yourself in the app: view and correct your data, and delete the account. For access or a copy of your data, get in touch.',
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
      <h2 className="detail-title">{c.title}</h2>
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
