import { useId } from 'react'
import { formatInteger } from '../../utils/format.js'
import { STAT_FIELDS } from './quoteCatalog.js'
import { formatDate, formatDecimal } from './quoteFormat.js'
import './StatsPanel.css'

function formatStat(key, value) {
  if (value === null || value === undefined) return '—'
  return key === 'rating' ? formatDecimal(value) : formatInteger(value)
}

export default function StatsPanel({ stats }) {
  const titleId = useId()
  return (
    <section className="stats-panel" aria-labelledby={titleId}>
      <h2 id={titleId}>Estadísticas de la temporada</h2>
      <dl className="stats-panel__list">
        {STAT_FIELDS.map(({ key, label }) => (
          <div key={key}>
            <dt>{label}</dt>
            <dd>{formatStat(key, stats?.[key])}</dd>
          </div>
        ))}
      </dl>
      {stats?.fetchedAt ? <p className="stats-panel__updated">Actualizado el {formatDate(stats.fetchedAt)}</p> : null}
    </section>
  )
}
