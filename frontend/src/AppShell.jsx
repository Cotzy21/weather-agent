import { useEffect, useRef, useState } from 'react'
import { useI18n } from './i18n.jsx'
import Icon from './Icon.jsx'
import { DESTINATIONS } from './navigation.js'


const PRIMARY = DESTINATIONS.slice(0, 3)

/** Shared desktop navigation and a thumb-reachable mobile bar. Auth stays in App. */
export default function AppShell({ tab, onNavigate, session, theme, onToggleTheme, slow, wide, children }) {
  const { t, lang, setLang } = useI18n()
  const [menuOpen, setMenuOpen] = useState(false)
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('sidebar-collapsed') === '1')
  const dialog = useRef(null)
  const menuTrigger = useRef(null)
  const main = useRef(null)
  useEffect(() => { window.scrollTo({ top: 0, behavior: 'instant' }) }, [tab])
  const current = DESTINATIONS.find((item) => item.id === tab)
  const moreSelected = !PRIMARY.some((item) => item.id === tab)

  useEffect(() => {
    try { localStorage.setItem('sidebar-collapsed', collapsed ? '1' : '0') } catch { /* storage can be unavailable */ }
  }, [collapsed])
  useEffect(() => {
    const node = dialog.current
    if (menuOpen && !node.open) node.showModal()
    if (!menuOpen && node.open) node.close()
  }, [menuOpen])
  // Native dialog supplies focus containment, Escape and an inert background.
  function navigate(id) {
    setMenuOpen(false)
    onNavigate(id)
    requestAnimationFrame(() => { if (!dialog.current?.open) main.current?.focus({ preventScroll: true }) })
  }
  const navButton = (item) => <button key={item.id} className={`shell-nav-item ${tab === item.id ? 'active' : ''}`} aria-current={tab === item.id ? 'page' : undefined} onClick={() => navigate(item.id)} title={t(item.label)}><Icon name={item.icon} /><span>{t(item.label)}</span></button>
  const utilities = <>
    <button className="shell-nav-item" onClick={() => setLang(lang === 'nb' ? 'en' : 'nb')}><Icon name="globe" /><span>{lang === 'nb' ? 'English' : 'Norsk'}</span></button>
    <button className="shell-nav-item" onClick={onToggleTheme}><Icon name={theme === 'light' ? 'recovery' : 'sun'} /><span>{t(theme === 'light' ? 'Mørk modus' : 'Lys modus')}</span></button>
    {navButton({ id: 'konto', icon: 'user', label: session ? 'Min konto' : 'Logg inn' })}
  </>

  return <div className={`app app-shell ${collapsed ? 'nav-collapsed' : ''}`} data-tab={tab}>
    <a className="skip-link" href="#main-content">{t('Hopp til innhold')}</a>
    {slow && <div className="wake-banner" role="status">{t('Vekker serveren … første innlasting kan ta opptil ett minutt.')}</div>}
    <aside className="app-sidebar" aria-label={t('Hovedmeny')}>
      <button className="shell-brand" onClick={() => navigate('hjem')} aria-label="Turvær"><Icon name="mountain" size={26} /><span>Turvær</span></button>
      <nav className="shell-nav" aria-label={t('Hovedmeny')}>{DESTINATIONS.map(navButton)}</nav>
      <div className="shell-utilities">{utilities}</div>
      <button className="shell-collapse" aria-label={t(collapsed ? 'Utvid menyen' : 'Minimer menyen')} onClick={() => setCollapsed(!collapsed)}><Icon name={collapsed ? 'chevron' : 'back'} size={18} /></button>
    </aside>
    <header className="mobile-header">
      <button className="shell-brand" onClick={() => navigate('hjem')}><Icon name="mountain" size={24} /><span>Turvær</span></button>
      <span className="mobile-location">{t(current?.label ?? (session ? 'Min konto' : 'Logg inn'))}</span>
      <button className="icon-button" aria-label={t(session ? 'Min konto' : 'Logg inn')} onClick={() => navigate('konto')} aria-current={tab === 'konto' ? 'page' : undefined}><Icon name="user" /></button>
    </header>
    <main id="main-content" className={`main ${wide ? 'wide' : ''}`} tabIndex={-1} ref={main}>{children}</main>
    <nav className="bottom-nav" aria-label={t('Hovedmeny')}>
      {PRIMARY.map(navButton)}
      <button ref={menuTrigger} className={`shell-nav-item ${moreSelected || menuOpen ? 'active' : ''}`} aria-haspopup="dialog" aria-expanded={menuOpen} aria-controls="more-navigation" onClick={() => setMenuOpen(true)}><Icon name="more" /><span>{t('Mer')}</span></button>
    </nav>
    <dialog id="more-navigation" ref={dialog} className="navigation-dialog" aria-labelledby="more-navigation-title" onCancel={() => setMenuOpen(false)} onClose={() => { setMenuOpen(false); if (document.activeElement === document.body) menuTrigger.current?.focus() }} onClick={(event) => { if (event.target === event.currentTarget) setMenuOpen(false) }}>
      <div className="navigation-dialog-inner">
        <div className="dialog-heading"><h2 id="more-navigation-title">{t('Mer i Turvær')}</h2><button className="icon-button" autoFocus aria-label={t('Lukk menyen')} onClick={() => setMenuOpen(false)}><Icon name="close" /></button></div>
        <nav aria-label={t('Flere sider')}>{DESTINATIONS.slice(3).map(navButton)}</nav>
        <div className="dialog-utilities">{utilities}</div>
      </div>
    </dialog>
  </div>
}
