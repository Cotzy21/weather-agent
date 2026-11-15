import { useState } from 'react'
import { hasPrivateGarmin, openPrivateGarmin } from './garminOwner.js'
import Sheet from './Sheet.jsx'
import { useI18n } from './i18n.jsx'
import './garminInsights.css'

export default function GarminImport({ onClose, userId }) {
  const { t, lang } = useI18n()
  const [failed, setFailed] = useState(false)
  const privateMode = Boolean(userId) && hasPrivateGarmin()
  const title = t('Garmin-import er satt på pause')

  async function openLocal() {
    setFailed(false)
    try { await openPrivateGarmin(userId, lang === 'en') }
    catch { setFailed(true) }
  }

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
      {privateMode && <>
        <p>{lang === 'nb' ? 'Dette private iOS-bygget kan hente egne Garmin-aktiviteter og lagre dem kryptert på enheten.' : 'This private iOS build can retrieve your own Garmin activities and save them encrypted on the device.'}</p>
        <button type="button" className="train-action" onClick={openLocal}>{lang === 'nb' ? 'Åpne privat Garmin-lager' : 'Open private Garmin vault'}</button>
        {failed && <p className="error" role="alert">{lang === 'nb' ? 'Kunne ikke åpne lokalt Garmin-lager.' : 'Could not open the local Garmin vault.'}</p>}
      </>}
      <button type="button" className="train-action" onClick={onClose}>{t('Lukk')}</button>
    </Sheet>
  )
}
