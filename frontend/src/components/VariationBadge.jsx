import { variation } from '../features/player-detail/quoteUtils.js'
import { formatDecimal, formatPercent, formatSignedCredits } from '../features/player-detail/quoteFormat.js'
import { formatCredits } from '../utils/format.js'
import './VariationBadge.css'

const SYMBOLS = { up: '▲', down: '▼', flat: '=' }
const VERBS = { up: 'Subió', down: 'Bajó' }

function describe({ delta, percent, direction }) {
  if (direction === 'flat') return 'Sin cambios'
  const absPercent = Math.round(Math.abs(percent) * 100) / 100
  return `${VERBS[direction]} ${formatCredits(Math.abs(delta))} (${formatDecimal(absPercent)} %)`
}

export default function VariationBadge({ current, previous }) {
  const change = variation(current, previous)
  if (!change) {
    return <span className="variation-badge variation-badge--none" role="img" aria-label="Sin variación disponible">—</span>
  }

  return (
    <span className={`variation-badge variation-badge--${change.direction}`} role="img" aria-label={describe(change)}>
      {SYMBOLS[change.direction]} {formatSignedCredits(change.delta)} ({formatPercent(change.percent)})
    </span>
  )
}
