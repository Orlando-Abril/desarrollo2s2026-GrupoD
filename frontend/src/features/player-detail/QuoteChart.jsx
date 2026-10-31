import { useId } from 'react'
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import Casillero from '../../components/Casillero.jsx'
import EstadoPanel from '../../components/EstadoPanel.jsx'
import { LEAGUES } from '../../domain/catalogo.js'
import { quoteStatusLabel } from '../../utils/comparison.js'
import { formatCredits } from '../../utils/format.js'
import { QUOTE_RANGES, STRATEGIES } from './quoteCatalog.js'
import { formatDate, formatDateTime } from './quoteFormat.js'
import { hasSameDayPoints } from './quoteUtils.js'
import './QuoteChart.css'

function strategyLabel(strategy) {
  return STRATEGIES[strategy]?.label ?? strategy
}

function prefersReducedMotion() {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

export function QuoteTooltip({ active, payload, sameDay }) {
  if (!active || !payload?.length) return null
  const point = payload[0].payload
  return (
    <div className="quote-tooltip">
      <strong>{sameDay ? formatDateTime(point.calculatedAt) : formatDate(point.calculatedAt)}</strong>
      <span>{formatCredits(point.value)}</span>
      <span>{strategyLabel(point.strategy)}</span>
      <span>{`Versión ${point.version}`}</span>
    </div>
  )
}

function OptionGroup({ legend, name, options, value, onChange }) {
  return (
    <fieldset className="quote-chart__options">
      <legend>{legend}</legend>
      {Object.entries(options).map(([key, option]) => (
        <label key={key}>
          <input type="radio" name={name} value={key} checked={value === key} onChange={() => onChange(key)} />
          <span>{option.label}</span>
        </label>
      ))}
    </fieldset>
  )
}

function ChartFigure({ points, caption, league }) {
  const sameDay = hasSameDayPoints(points)
  const formatPointDate = sameDay ? formatDateTime : formatDate
  const colorVar = LEAGUES[league]?.colorVar ?? '--tinta-2'

  return (
    <figure className="quote-chart__figure" style={{ '--quote-line': `var(${colorVar})` }}>
      <figcaption>{caption}</figcaption>
      <div className="quote-chart__canvas">
        <ResponsiveContainer width="100%" height={280}>
          <LineChart data={points} role="img" aria-label={caption} margin={{ top: 12, right: 16, bottom: 8, left: 8 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']} tickFormatter={(t) => formatDate(t)} minTickGap={24} />
            <YAxis dataKey="value" tickFormatter={formatCredits} width={76} domain={['auto', 'auto']} />
            <Tooltip content={<QuoteTooltip sameDay={sameDay} />} />
            <Line type="monotone" dataKey="value" strokeWidth={3} isAnimationActive={!prefersReducedMotion()} />
          </LineChart>
        </ResponsiveContainer>
      </div>
      <table className="quote-chart__table">
        <caption>{caption}</caption>
        <thead>
          <tr><th scope="col">Fecha</th><th scope="col">Valor</th><th scope="col">Estrategia</th><th scope="col">Versión</th></tr>
        </thead>
        <tbody>
          {points.map((point) => (
            <tr key={`${point.t}-${point.version}`}>
              <td>{formatPointDate(point.calculatedAt)}</td>
              <td>{formatCredits(point.value)}</td>
              <td>{strategyLabel(point.strategy)}</td>
              <td>{point.version}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  )
}

// El trazo lo pinta el CSS por clase; el color también se pasa al Tooltip, que lo usa como estilo HTML.
const SERIES_TONES = [{ tone: 'market', color: 'var(--azul)' }, { tone: 'alternative', color: 'var(--amarillo)' }]

function comparisonCell(point, code) {
  if (point[code] === null) return 'Sin cotización'
  return `${formatCredits(point[code])} · ${quoteStatusLabel(point.details[code]?.status)}`
}

function ComparisonFigure({ points, codes, caption, partialError, onRetry }) {
  const series = codes.map((code, index) => ({ code, label: strategyLabel(code), ...SERIES_TONES[index] }))
  return <figure className="quote-chart__figure quote-chart__figure--comparison">
    <figcaption>{caption}</figcaption>
    {partialError ? <p><output>Una de las curvas no está disponible.</output> <button type="button" onClick={onRetry}>Reintentar</button></p> : null}
    <div className="quote-chart__canvas">
      <ResponsiveContainer width="100%" height={280}>
        <LineChart data={points} role="img" aria-label={caption} margin={{ top: 12, right: 16, bottom: 8, left: 8 }}>
          <CartesianGrid strokeDasharray="3 3" />
          <XAxis dataKey="t" type="number" scale="time" domain={['dataMin', 'dataMax']} tickFormatter={(t) => formatDate(t)} minTickGap={24} />
          <YAxis tickFormatter={formatCredits} width={76} domain={['auto', 'auto']} />
          <Tooltip labelFormatter={(t) => formatDateTime(t)} formatter={(value, name) => [formatCredits(value), name]} />
          {series.map(({ code, label, tone, color }) => <Line key={code} name={label} className={`quote-chart__series--${tone}`} stroke={color} type="monotone" dataKey={code} strokeWidth={3} connectNulls={false} isAnimationActive={!prefersReducedMotion()} />)}
        </LineChart>
      </ResponsiveContainer>
    </div>
    <ul className="quote-chart__legend">
      {series.map(({ code, label, tone }) => <li key={code}><i className={`quote-chart__swatch quote-chart__series--${tone}`} aria-hidden="true" />{label}</li>)}
    </ul>
    <table className="quote-chart__table">
      <caption>{caption}</caption>
      <thead><tr><th scope="col">Fecha</th>{series.map(({ code, label }) => <th key={code} scope="col">{label}</th>)}</tr></thead>
      <tbody>{points.map((point) => <tr key={point.t}>
        <td>{formatDateTime(point.calculatedAt)}</td>
        {series.map(({ code }) => <td key={code}>{comparisonCell(point, code)}</td>)}
      </tr>)}</tbody>
    </table>
  </figure>
}

export default function QuoteChart({ points, status, strategy, range, league, onStrategyChange, onRangeChange, onRetry, comparisonCodes = null, comparisonPoints = [], partialError = false }) {
  const titleId = useId()
  const groupName = useId()
  const comparison = Boolean(comparisonCodes?.length)
  const caption = comparison ? `${comparisonCodes.map(strategyLabel).join(' / ')} · ${QUOTE_RANGES[range].label} · ${comparisonPoints.length} fechas` : `${strategyLabel(strategy)} · ${QUOTE_RANGES[range].label} · ${points.length} cotizaciones`

  let content
  if (status === 'loading') {
    content = (
      <div className="quote-chart__loading" aria-busy="true" aria-label="Cargando evolución…">
        <Casillero variant="cargando">Cargando evolución…</Casillero>
      </div>
    )
  } else if (status === 'error') {
    content = (
      <EstadoPanel stamp="Sin conexión" stampVariant="error" title="No pudimos cargar la evolución" action={{ label: 'Reintentar', onClick: onRetry }}>
        No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.
      </EstadoPanel>
    )
  } else if (status === 'empty') {
    content = (
      <EstadoPanel stamp="Sin datos" title="Todavía sin cotizaciones">
        No hay cotizaciones para esta estrategia y rango. Probá con otro rango o estrategia.
      </EstadoPanel>
    )
  } else {
    content = comparison ? <ComparisonFigure points={comparisonPoints} codes={comparisonCodes} caption={caption} partialError={partialError} onRetry={onRetry} /> : <ChartFigure points={points} caption={caption} league={league} />
  }

  return (
    <section className="quote-chart" aria-labelledby={titleId}>
      <h2 id={titleId}>Evolución de la cotización</h2>
      <div className="quote-chart__controls">
        {comparison ? null : <OptionGroup legend="Estrategia" name={`${groupName}-strategy`} options={STRATEGIES} value={strategy} onChange={onStrategyChange} />}
        <OptionGroup legend="Rango" name={`${groupName}-range`} options={QUOTE_RANGES} value={range} onChange={onRangeChange} />
      </div>
      {content}
    </section>
  )
}
