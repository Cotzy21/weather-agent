/** Compact atmospheric heading shared by working screens; all figures remain in the content below. */
export default function ObservatoryHeading({ title, description, children, action, scenic = false }) {
  return <header className={`observatory-heading${scenic ? ' observatory-heading-scenic' : ''}`}>
    {scenic && <div className="observatory-heading-sky" aria-hidden="true" />}
    <div className="observatory-heading-copy">
      <h1>{title}</h1>
      {description && <p>{description}</p>}
      {children}
    </div>
    {action && <div className="observatory-heading-action">{action}</div>}
    {scenic && <svg className="observatory-heading-arc" viewBox="0 0 400 200" fill="none" aria-hidden="true"><path d="M30 200V170a170 170 0 0 1 340 0v30M45 200V170a155 155 0 0 1 310 0v30M60 200V170a140 140 0 0 1 280 0v30M0 170h400M200 0v200" stroke="currentColor" /></svg>}
  </header>
}
