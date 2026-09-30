import { useI18n } from './i18n.jsx'

// Garmin-import på Trening-siden. CSV gir alle aktiviteter i historikken (bare totaler); FIT/ZIP gir øvelser og
// vekter for styrkeøkter, og leses her på enheten (serveren tar bare imot ferdig tolkede økter).
export default function GarminImport({ imp }) {
  const { t } = useI18n()
  const p = imp.progress
  const r = imp.result

  let progress = null
  if (imp.busy) {
    if (imp.kind === 'csv') progress = t('Importerer … {done}/{total}', { done: p?.done ?? 0, total: p?.total ?? '…' })
    else if (p?.phase === 'match') progress = t('Kobler øvelsene til appens øvelser …')
    else if (p?.phase === 'send') progress = t('Lagrer økter … {sent}/{of}', { sent: p.sent, of: p.of })
    else progress = t('Leser filer … {files} FIT-filer, {n} styrkeøkter funnet', { files: p?.fitFiles ?? 0, n: p?.strength ?? 0 })
  }

  let summary = null
  if (r && imp.kind === 'csv') {
    summary = r.rows === 0
      ? t('Fant ingen aktiviteter i fila – er det CSV-eksporten fra Garmin Connect?')
      : `✓ ${t('{n} økter lagt til i historikken', { n: r.imported })}`
        + (r.merged ? ` · ${t('{n} slått sammen med økter fra FIT', { n: r.merged })}` : '')
        + (r.skipped ? ` · ${t('{n} fantes fra før', { n: r.skipped })}` : '')
  } else if (r && imp.kind === 'fit') {
    summary = r.strength === 0
      ? t('Fant ingen styrkeøkter med øvelser i filene ({n} FIT-filer lest).', { n: r.fitFiles })
      : `✓ ${t('{n} økter fikk øvelser og vekter', { n: r.merged + r.imported })}`
        + (r.merged ? ` (${t('{n} fylt inn på økter fra CSV', { n: r.merged })})` : '')
        + (r.skipped ? ` · ${t('{n} fantes fra før', { n: r.skipped })}` : '')
        + (r.names?.total ? ` · ${t('{matched} av {total} øvelsesnavn koblet til appens øvelser', { matched: r.names.matched, total: r.names.total })}` : '')
        + (r.notStrength ? ` · ${t('{n} andre aktiviteter hoppet over', { n: r.notStrength })}` : '')
        + (r.unreadable ? ` · ${t('{n} filer kunne ikke leses', { n: r.unreadable })}` : '')
  }

  return (
    <section className="garmin-import" data-reveal aria-label={t('Garmin-import')}>
      <div className="gi-head">
        <strong>⌚ {t('Importer fra Garmin')}</strong>
      </div>
      <div className="gi-buttons">
        <label className={`train-action gi-btn ${imp.busy ? 'disabled' : ''}`}>
          <span className="ta-icon" aria-hidden="true">📄</span>{t('Last opp CSV')}
          <input type="file" accept=".csv,text/csv" hidden disabled={imp.busy}
                 onChange={(e) => { imp.runCsv(e.target.files[0]); e.target.value = '' }} />
        </label>
        <label className={`train-action gi-btn ${imp.busy ? 'disabled' : ''}`}>
          <span className="ta-icon" aria-hidden="true">🏋️</span>{t('Last opp FIT / ZIP')}
          <input type="file" accept=".fit,.zip,application/zip" multiple hidden disabled={imp.busy}
                 onChange={(e) => { imp.runFit(e.target.files); e.target.value = '' }} />
        </label>
      </div>
      <p className="muted gi-note">
        {t('CSV legger alle øktene i historikken. For vekter og øvelser (og progresjon per øvelse) trengs FIT-filer.')}
      </p>
      <details className="gi-help">
        <summary>{t('Slik får du CSV og FIT-filer')}</summary>
        <ol>
          <li>{t('CSV: Garmin Connect (nettside) → Aktiviteter → Alle aktiviteter → «Eksporter CSV». Bla ned til alle er lastet før du eksporterer.')}</li>
          <li>{t('FIT (alle økter på én gang): Garmin Connect → Kontoinnstillinger → Datahåndtering → «Eksporter data». Du får en ZIP på e-post; last den opp her uten å pakke den ut.')}</li>
          <li>{t('FIT (én økt): åpne aktiviteten → tannhjulet → «Eksporter original».')}</li>
          <li>{t('Filene leses på enheten din. Bare øvelser, sett og vekter sendes til serveren.')}</li>
        </ol>
      </details>
      {progress && <p className="muted" role="status">{progress}</p>}
      {summary && <p className={summary.startsWith('✓') ? 'success' : 'muted'} role="status">{summary}</p>}
      {imp.error && <p className="error" role="alert">{imp.error}</p>}
    </section>
  )
}
