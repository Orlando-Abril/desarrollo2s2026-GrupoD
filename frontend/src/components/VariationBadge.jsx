import { variation } from '../features/player-detail/quoteUtils.js'
import { formatDecimal, formatPercent, formatSignedCredits } from '../features/player-detail/quoteFormat.js'
import { formatCredits } from '../utils/format.js'
import './VariationBadge.css'

const SYMBOLS = { up: '▲', down: '▼', flat: '=' }
const VERBS = { up: 'Subió', down: 'Bajó' }

function directionOf(value) {
  const rounded = Math.round(value * 100) / 100
  if (rounded > 0) return 'up'
  if (rounded < 0) return 'down'
  return 'flat'
}

// Con current/previous (detalle) se calcula el cambio con créditos; con percent (ranking) llega ya calculado.
function changeFrom({ current, previous, percent }) {
  if (percent === undefined) return variation(current, previous)
  if (percent === null) return null
  return { percent, direction: directionOf(percent) }
}

function describe({ delta, percent, direction }) {
  if (direction === 'flat') return 'Sin cambios'
  const absPercent = `${formatDecimal(Math.round(Math.abs(percent) * 100) / 100)} %`
  if (delta === undefined) return `${VERBS[direction]} ${absPercent}`
  return `${VERBS[direction]} ${formatCredits(Math.abs(delta))} (${absPercent})`
}

function text({ delta, percent }) {
  if (delta === undefined) return formatPercent(percent)
  return `${formatSignedCredits(delta)} (${formatPercent(percent)})`
}

export default function VariationBadge(props) {
  const change = changeFrom(props)
  if (!change) {
    return <span className="variation-badge variation-badge--none" role="img" aria-label="Sin variación disponible">—</span>
  }

  return (
    <span className={`variation-badge variation-badge--${change.direction}`} role="img" aria-label={describe(change)}>
      {SYMBOLS[change.direction]} {text(change)}
    </span>
  )
}
