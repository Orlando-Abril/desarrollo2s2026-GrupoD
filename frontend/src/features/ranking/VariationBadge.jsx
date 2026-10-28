import './VariationBadge.css'

const percentageFormatter = new Intl.NumberFormat('es-AR', {
  maximumFractionDigits: 2,
})

export default function VariationBadge({ value }) {
  if (value === null || value === undefined) {
    return (
      <span className="variation-badge variation-badge--unknown" aria-label="Sin dato">
        <span aria-hidden="true">—</span> Sin dato
      </span>
    )
  }

  if (value === 0) {
    return (
      <span className="variation-badge variation-badge--flat" aria-label="Sin cambios">
        <span aria-hidden="true">→</span> 0 %
      </span>
    )
  }

  const isPositive = value > 0
  const absoluteValue = percentageFormatter.format(Math.abs(value))
  const visibleValue = `${isPositive ? '+' : '-'}${absoluteValue} %`
  const direction = isPositive ? 'Subió' : 'Bajó'

  return (
    <span
      className={`variation-badge variation-badge--${isPositive ? 'up' : 'down'}`}
      aria-label={`${direction} ${absoluteValue} por ciento`}
    >
      <span aria-hidden="true">{isPositive ? '↗' : '↘'}</span> {visibleValue}
    </span>
  )
}
