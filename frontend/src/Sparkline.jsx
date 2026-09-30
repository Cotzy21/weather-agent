// Liten trendlinje. Tegnes som SVG uten bibliotek. `values` er tall i tidsrekkefølge.
export default function Sparkline({ values, width = 96, height = 28, label }) {
  if (!values || values.length < 2) return null
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  const pad = 3
  const points = values.map((v, i) => {
    const x = pad + (i / (values.length - 1)) * (width - pad * 2)
    const y = height - pad - ((v - min) / span) * (height - pad * 2)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  })
  const up = values.at(-1) >= values[0]
  return (
    <svg className={`sparkline ${up ? 'up' : 'down'}`} width={width} height={height} viewBox={`0 0 ${width} ${height}`}
         role="img" aria-label={label}>
      <polyline points={points.join(' ')} fill="none" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" />
      <circle cx={points.at(-1).split(',')[0]} cy={points.at(-1).split(',')[1]} r="2.5" />
    </svg>
  )
}
