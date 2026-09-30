import { useI18n } from './i18n.jsx'
import { WIDGETS } from './dashboardLayout.js'

const byId = Object.fromEntries(WIDGETS.map((w) => [w.id, w]))

// Ark for å velge hvilke kort som vises på Hjem og i hvilken rekkefølge.
export default function DashboardCustomizer({ layout, onChange, onClose }) {
  const { t } = useI18n()

  function move(i, delta) {
    const next = [...layout]
    const [item] = next.splice(i, 1)
    next.splice(i + delta, 0, item)
    onChange(next)
  }

  return (
    <div className="sheet-backdrop" onClick={onClose}>
      <div className="sheet picker" role="dialog" aria-label={t('Tilpass dashboardet')} onClick={(e) => e.stopPropagation()}>
        <span className="sheet-handle" aria-hidden="true" />
        <div className="picker-head">
          <strong>{t('Tilpass dashboardet')}</strong>
          <button className="del" onClick={onClose} aria-label={t('Lukk')}>✕</button>
        </div>
        <p className="muted custom-hint">{t('Slå av det du ikke bruker, og flytt det viktigste øverst.')}</p>
        <ul className="picker-list custom-list">
          {layout.map((w, i) => (
            <li key={w.id} className={w.on ? '' : 'off'}>
              <span className="custom-icon" aria-hidden="true">{byId[w.id].icon}</span>
              <span className="custom-label">{t(byId[w.id].label)}</span>
              <button className="move" disabled={i === 0} aria-label={t('Flytt opp')} onClick={() => move(i, -1)}>▲</button>
              <button className="move" disabled={i === layout.length - 1} aria-label={t('Flytt ned')} onClick={() => move(i, 1)}>▼</button>
              <button
                role="switch"
                aria-checked={w.on}
                aria-label={t(byId[w.id].label)}
                className={`switch ${w.on ? 'on' : ''}`}
                onClick={() => onChange(layout.map((x) => (x.id === w.id ? { ...x, on: !x.on } : x)))}
              ><span /></button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
