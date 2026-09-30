// Valgknapper side om side i stedet for <select>: alle valg synlige, ett trykk.
export default function ChoiceChips({ options, value, onChange, label }) {
  return (
    <div className="choice-chips" role="radiogroup" aria-label={label}>
      {options.map((o) => {
        const on = String(o.v) === String(value)
        return (
          <button
            type="button"
            key={String(o.v)}
            role="radio"
            aria-checked={on}
            className={`choice-chip ${on ? 'on' : ''}`}
            onClick={() => onChange(o.v)}
          >{o.t}</button>
        )
      })}
    </div>
  )
}
