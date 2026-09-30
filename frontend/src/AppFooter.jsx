import { useI18n } from './i18n.jsx'

// Kildehenvisninger vi er pålagt av lisensene (MET og Open-Meteo: CC BY 4.0, OpenStreetMap: ODbL, Matvaretabellen: NLOD),
// pluss lenke til personvernerklæringen. Leaflet viser i tillegg OSM-kreditering nede i hjørnet av selve kartet.
function Ext({ href, children }) {
  return <a href={href} target="_blank" rel="noopener noreferrer">{children}</a>
}

export default function AppFooter({ onOpenPrivacy }) {
  const { t } = useI18n()
  return (
    <footer className="app-footer">
      <p>
        {t('Værdata fra')} <Ext href="https://www.met.no/">{t('Meteorologisk institutt')}</Ext> {t('og')}{' '}
        <Ext href="https://open-meteo.com/">Open-Meteo.com</Ext> (CC BY 4.0)
        {' · '}
        {t('Kartdata')} © <Ext href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</Ext>
        {' · '}
        {t('Næringsdata')}: <Ext href="https://www.matvaretabellen.no/">Matvaretabellen</Ext>
      </p>
      <p>
        <button className="link-btn" onClick={onOpenPrivacy}>{t('Personvern')}</button>
      </p>
    </footer>
  )
}
