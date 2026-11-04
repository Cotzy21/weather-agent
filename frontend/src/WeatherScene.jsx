/** Decorative, locally drawn SVG. Loading motion conveys waiting, not progress. */
export default function WeatherScene({ busy = false, compact = false }) {
  return (
    <div className={`weather-scene ${busy ? 'is-searching' : ''} ${compact ? 'compact' : ''}`} aria-hidden="true">
      <svg viewBox="0 0 320 160" fill="none" focusable="false">
        <circle className="scene-sun" cx="243" cy="42" r="20" />
        <g className="scene-cloud">
          <path d="M190 52c-7-16-31-12-32 4-17-4-24 18-8 24h55c19-5 12-31-5-27-2-1-6-1-10-1Z" />
        </g>
        <path className="scene-ridge far" d="m24 137 75-88 49 58 38-37 87 67Z" />
        <path className="scene-ridge near" d="m102 137 82-101 112 101Z" />
        <path className="scene-snow" d="m159 67 25-31 34 31-22-7-12 9-10-9Z" />
        <path className="scene-ground" d="M22 137h276" />
        <path className="scene-trail" pathLength="1" d="M114 138c57-1 99-10 69-25-35-17-22-23 14-28" />
        <circle className="scene-waypoint" cx="197" cy="85" r="4" />
        {busy && <circle className="scene-traveler" r="4" />}
      </svg>
    </div>
  )
}
