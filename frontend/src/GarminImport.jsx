import Sheet from './Sheet.jsx'
import { useI18n } from './i18n.jsx'
import './garminInsights.css'

export default function GarminImport({ onClose }) {
  const { t } = useI18n()
  const title = t('Garmin-import er satt på pause')

  return (
    <Sheet className="sheet garmin-privacy" label={title} onClose={onClose}>
      <span className="sheet-handle" aria-hidden="true" />
      <h2>{title}</h2>
      <p>{t('Søvn, puls, treningsøkter og andre målinger kan fortelle mye om helsen din. Importen er stengt mens vi gjør klar en løsning som beskytter disse dataene.')}</p>
      <dl>
        <div>
          <dt>{t('Nye importer')}</dt>
          <dd>{t('Du kan foreløpig ikke legge inn en Garmin-nøkkel eller sende Garmin-filer til Turvær.')}</dd>
        </div>
        <div>
          <dt>{t('Tidligere importer')}</dt>
          <dd>{t('Målinger fra tidligere importer ligger fortsatt på Turvær-kontoen. De har ikke ende-til-ende-kryptering. Du kan slette målingene under Restitusjon og importerte økter i treningsloggen.')}</dd>
        </div>
      </dl>
      <button type="button" className="train-action" onClick={onClose}>{t('Lukk')}</button>
    </Sheet>
  )
}
