import './SummaryList.css'

/** Resumen término → valor de una confirmación; `rows` es una lista de pares [término, valor]. */
export default function SummaryList({ rows }) {
  return (
    <dl className="summary-list">
      {rows.map(([term, value]) => (
        <div key={term}>
          <dt>{term}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
