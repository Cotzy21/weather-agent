import { useLayoutEffect, useRef, useState } from 'react'
import gsap from 'gsap'
import { useI18n } from './i18n.jsx'

/**
 * Interaktiv kroppsmodell: velg gutt/jente og front/rygg, trykk på musklene
 * du vil trene, og få en ferdig økt inn i byggeren. Muskel -> øvelser-mappingen
 * matcher øvelsesnavnene i TrainingView (og dermed progresjons-søket).
 */
const MUSCLES = {
  bryst: { label: 'Bryst', view: 'front', exercises: ['Benkpress', 'Hantelpress', 'Flies'] },
  skuldre: { label: 'Skuldre', view: 'front', exercises: ['Skulderpress', 'Sidehev', 'Bakre flies'] },
  biceps: { label: 'Biceps', view: 'front', exercises: ['Bicepscurl', 'Hammercurl'] },
  mage: { label: 'Mage', view: 'front', exercises: ['Planke', 'Hanging leg raise', 'Cable crunch'] },
  quads: { label: 'Lår (fremside)', view: 'front', exercises: ['Knebøy', 'Leg extension', 'Utfall'] },
  trapes: { label: 'Nakke/trapes', view: 'bak', exercises: ['Opprekk', 'Face pulls'] },
  rygg: { label: 'Rygg', view: 'bak', exercises: ['Nedtrekk', 'Sittende roing', 'Pull-ups'] },
  korsrygg: { label: 'Korsrygg', view: 'bak', exercises: ['Markløft'] },
  triceps: { label: 'Triceps', view: 'bak', exercises: ['Triceps pushdown', 'Skullcrushers'] },
  setet: { label: 'Setet', view: 'bak', exercises: ['Hip thrust', 'Bulgarske utfall'] },
  hamstrings: { label: 'Lår (bakside)', view: 'bak', exercises: ['Rumensk markløft', 'Leg curl'] },
  legger: { label: 'Legger', view: 'bak', exercises: ['Tåhev'] },
}

/* Symmetriske muskelformer (venstre/høyre) + midtstilte, per visning. */
const SHAPES = {
  front: [
    { id: 'skuldre', kind: 'pair', cx: 76, cy: 96, rx: 15, ry: 11 },
    { id: 'bryst', kind: 'pair', cx: 93, cy: 122, rx: 16, ry: 13 },
    { id: 'biceps', kind: 'pair', cx: 61, cy: 140, rx: 9, ry: 17 },
    { id: 'mage', kind: 'rect', x: 94, y: 142, w: 32, h: 58, r: 12 },
    { id: 'quads', kind: 'pair', cx: 96, cy: 292, rx: 13, ry: 38 },
  ],
  bak: [
    { id: 'trapes', kind: 'mid', cx: 110, cy: 94, rx: 24, ry: 12 },
    { id: 'rygg', kind: 'pair', cx: 93, cy: 136, rx: 15, ry: 23 },
    { id: 'triceps', kind: 'pair', cx: 61, cy: 140, rx: 9, ry: 17 },
    { id: 'korsrygg', kind: 'mid', cx: 110, cy: 182, rx: 13, ry: 16 },
    { id: 'setet', kind: 'pair', cx: 97, cy: 237, rx: 14, ry: 15 },
    { id: 'hamstrings', kind: 'pair', cx: 96, cy: 300, rx: 12, ry: 33 },
    { id: 'legger', kind: 'pair', cx: 95, cy: 383, rx: 10, ry: 22 },
  ],
}

/**
 * Silhuett (hode, torso, armer, bein) – tegnes som glødende kontur.
 * Jente og gutt har FAKTISK ulik figur: jenta har smalere skuldre, innsvingt
 * midje og bredere hofter; gutten bredere skuldre og rettere torso.
 */
function Silhouette({ gender }) {
  if (gender === 'jente') {
    return (
      <g className="body-outline">
        <circle cx="110" cy="36" r="20" />
        <path d="M104 55 L104 68 L116 68 L116 55" />
        {/* torso: smale skuldre -> innsvingt midje -> brede hofter */}
        <path d="M82 82 Q110 72 138 82 Q134 118 132 146 Q140 188 146 226 Q147 246 126 255 Q110 259 94 255 Q73 246 74 226 Q80 188 88 146 Q86 118 82 82 Z" />
        {/* armer (smalere) */}
        <path d="M82 84 Q66 102 68 140 Q68 170 62 202 L71 206 Q80 176 80 146" />
        <path d="M138 84 Q154 102 152 140 Q152 170 158 202 L149 206 Q140 176 140 146" />
        {/* bein (fra brede hofter) */}
        <path d="M90 252 Q92 312 96 352 Q98 398 96 434 L109 434 Q111 394 111 352 L112 300" />
        <path d="M130 252 Q128 312 124 352 Q126 398 128 434 L115 434 Q113 394 113 352 L108 300" />
      </g>
    )
  }
  return (
    <g className="body-outline">
      <circle cx="110" cy="36" r="21" />
      <path d="M102 56 L102 68 L118 68 L118 56" />
      {/* torso: brede skuldre -> midje -> hofter */}
      <path d="M70 80 Q110 68 150 80 L146 150 Q142 190 138 210 L142 236 Q128 252 110 252 Q92 252 78 236 L82 210 Q78 190 74 150 Z" />
      {/* armer */}
      <path d="M70 82 Q52 100 54 140 Q54 172 48 204 L58 208 Q68 176 68 144" />
      <path d="M150 82 Q168 100 166 140 Q166 172 172 204 L162 208 Q152 176 152 144" />
      {/* bein */}
      <path d="M84 246 Q86 310 90 350 Q92 396 90 432 L104 432 Q106 392 106 350 L110 300" />
      <path d="M136 246 Q134 310 130 350 Q128 396 130 432 L116 432 Q114 392 114 350 L110 300" />
    </g>
  )
}

function MuscleShape({ shape, selected, hovered, onClick, onHover }) {
  const cls = `muscle ${selected ? 'selected' : ''} ${hovered ? 'hovered' : ''}`
  const common = {
    className: cls,
    onClick: () => onClick(shape.id),
    onMouseEnter: () => onHover(shape.id),
    onMouseLeave: () => onHover(null),
  }
  if (shape.kind === 'rect') {
    return <rect x={shape.x} y={shape.y} width={shape.w} height={shape.h} rx={shape.r} {...common} />
  }
  if (shape.kind === 'mid') {
    return <ellipse cx={shape.cx} cy={shape.cy} rx={shape.rx} ry={shape.ry} {...common} />
  }
  // pair: speiles rundt midtaksen x=110
  return (
    <g {...common}>
      <ellipse cx={shape.cx} cy={shape.cy} rx={shape.rx} ry={shape.ry} />
      <ellipse cx={220 - shape.cx} cy={shape.cy} rx={shape.rx} ry={shape.ry} />
    </g>
  )
}

/* Treningsfokus styrer rep-området øktene legges opp med. */
const FOCUS = {
  styrke: { label: 'Styrke', reps: 5, hint: 'tunge løft, ca. 5 reps per sett' },
  volum: { label: 'Volum', reps: 10, hint: 'muskelvekst, ca. 10 reps per sett' },
}

/**
 * Fordel øvelser rundgang mellom de valgte musklene (i valgt rekkefølge),
 * til vi har `count` øvelser eller går tom. Med [bryst, rygg] og 4 øvelser
 * blir det altså 2 fra hver.
 */
function pickExercises(selectedIds, count) {
  const queues = selectedIds.map((id) => [...MUSCLES[id].exercises])
  const out = []
  let i = 0
  while (out.length < count && queues.some((q) => q.length)) {
    const q = queues[i % queues.length]
    if (q.length) out.push(q.shift())
    i++
  }
  return out
}

export default function MusclePicker({ onCreate }) {
  const { t } = useI18n()
  const [gender, setGender] = useState('gutt')
  const [view, setView] = useState('front')
  const [selected, setSelected] = useState([]) // rekkefølgen brukeren valgte i
  const [hovered, setHovered] = useState(null)
  const [focus, setFocus] = useState('styrke')
  const [exCount, setExCount] = useState(4)
  const [setCount, setSetCount] = useState(4)
  const [created, setCreated] = useState(false) // kort «✓ Lagt i økta»-kvittering
  const svgRef = useRef(null)

  // Kroppen "puster" svakt + glir inn ved bytte av visning/kjønn.
  useLayoutEffect(() => {
    const el = svgRef.current
    if (!el) return undefined
    const ctx = gsap.context(() => {
      gsap.fromTo(el, { opacity: 0, rotateY: 25 }, { opacity: 1, rotateY: 0, duration: 0.5, ease: 'power2.out' })
      gsap.to(el, { scale: 1.012, duration: 2.6, yoyo: true, repeat: -1, ease: 'sine.inOut', transformOrigin: '50% 45%' })
    }, el)
    return () => ctx.revert()
  }, [view, gender])

  function toggle(id) {
    setSelected((s) => (s.includes(id) ? s.filter((m) => m !== id) : [...s, id]))
  }

  function create() {
    const names = pickExercises(selected, exCount)
    onCreate(names, selected.map((id) => t(MUSCLES[id].label)), {
      sets: setCount,
      reps: FOCUS[focus].reps,
    })
    setSelected([])
    // Mikrointeraksjon: bekreft at øvelsene faktisk havnet i byggeren under.
    setCreated(true)
    setTimeout(() => setCreated(false), 2500)
  }

  const active = hovered ?? selected[selected.length - 1]

  return (
    <div className="muscle-picker" data-reveal>
      <div className="mp-head">
        <span className="ai-title">{t('🧬 Bygg økt fra kroppen')}</span>
        <div className="mp-toggles">
          <div className="seg" title={t('Velg kroppstype')}>
            <button className={gender === 'gutt' ? 'on' : ''} onClick={() => setGender('gutt')}>{t('Gutt')}</button>
            <button className={gender === 'jente' ? 'on' : ''} onClick={() => setGender('jente')}>{t('Jente')}</button>
          </div>
          <div className="seg" title={t('Snu kroppen for å nå muskler på begge sider')}>
            <button className={view === 'front' ? 'on' : ''} onClick={() => setView('front')}>{t('Front')}</button>
            <button className={view === 'bak' ? 'on' : ''} onClick={() => setView('bak')}>{t('Rygg')}</button>
          </div>
        </div>
      </div>

      <div className="mp-body">
        <svg ref={svgRef} viewBox="0 0 220 460" className={`body ${gender}`} role="img"
             aria-label={`Kroppsmodell (${gender}, ${view === 'front' ? 'forfra' : 'bakfra'})`}>
          <g>
            <Silhouette gender={gender} />
            {SHAPES[view].map((s) => (
              <MuscleShape key={s.id} shape={s}
                           selected={selected.includes(s.id)}
                           hovered={hovered === s.id}
                           onClick={toggle} onHover={setHovered} />
            ))}
          </g>
        </svg>

        <div className="mp-side">
          <p className="mp-active">{active ? t(MUSCLES[active].label) : t('Trykk på en muskel')}</p>
          {active && (
            <ul className="mp-exercises">
              {MUSCLES[active].exercises.map((e) => <li key={e}>{e}</li>)}
            </ul>
          )}
          <div className="mp-chips">
            {selected.map((id) => (
              <button key={id} className="type-chip active" onClick={() => toggle(id)}>
                {t(MUSCLES[id].label)} ✕
              </button>
            ))}
          </div>

          <div className="mp-plan">
            <div className="mp-plan-row">
              <span className="mp-plan-label">{t('Fokus')}</span>
              <div className="seg">
                {Object.entries(FOCUS).map(([key, f]) => (
                  <button key={key} className={focus === key ? 'on' : ''} onClick={() => setFocus(key)}>
                    {t(f.label)}
                  </button>
                ))}
              </div>
            </div>
            <div className="mp-plan-row">
              <span className="mp-plan-label">{t('Øvelser')}</span>
              <div className="stepper">
                <button onClick={() => setExCount((n) => Math.max(1, n - 1))} aria-label={t('Færre øvelser')}>−</button>
                <span>{exCount}</span>
                <button onClick={() => setExCount((n) => Math.min(10, n + 1))} aria-label={t('Flere øvelser')}>+</button>
              </div>
            </div>
            <div className="mp-plan-row">
              <span className="mp-plan-label">{t('Sett per øvelse')}</span>
              <div className="stepper">
                <button onClick={() => setSetCount((n) => Math.max(1, n - 1))} aria-label={t('Færre sett')}>−</button>
                <span>{setCount}</span>
                <button onClick={() => setSetCount((n) => Math.min(8, n + 1))} aria-label={t('Flere sett')}>+</button>
              </div>
            </div>
            <p className="mp-hint muted">{t(FOCUS[focus].label)}: {t(FOCUS[focus].hint)}.</p>
          </div>

          <button className="primary mp-create" disabled={!selected.length} onClick={create}>
            {t('⚡ Lag økt ({e} øvelser × {s} sett)', {
              e: Math.min(exCount, selected.reduce((n, id) => n + MUSCLES[id].exercises.length, 0)),
              s: setCount,
            })}
          </button>
          {created && <span className="success">{t('✓ Lagt i økta under')}</span>}
          <p className="mp-hint muted">{t('Musklene på baksiden/framsiden finner du under den andre knappen.')}</p>
        </div>
      </div>
    </div>
  )
}
