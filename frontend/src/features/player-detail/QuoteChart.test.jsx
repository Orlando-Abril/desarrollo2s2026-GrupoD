import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import QuoteChart, { QuoteTooltip } from './QuoteChart.jsx'
import { toQuotePoints } from './quoteUtils.js'
import { formatDate, formatDateTime } from './quoteFormat.js'

// jsdom mide 0×0 y no tiene ResizeObserver: se fija el tamaño del gráfico (research R2).
vi.mock('recharts', async (importOriginal) => {
  const { cloneElement } = await import('react')
  return {
    ...(await importOriginal()),
    ResponsiveContainer: ({ children }) => <div style={{ width: 800, height: 300 }}>{cloneElement(children, { width: 800, height: 300 })}</div>,
  }
})

const plain = (text) => text.replace(/\s/g, ' ')

function quote(calculatedAt, value, overrides = {}) {
  return { value, score: null, status: 'CALCULATED', strategy: 'OFFICIAL', version: 1, calculatedAt, ...overrides }
}

const POINTS = toQuotePoints([
  quote('2026-10-20T18:00:00Z', 1.3, { version: 2 }),
  quote('2026-10-01T18:00:00Z', 1.1),
  quote('2026-10-08T18:00:00Z', 1.2, { status: 'NO_STATS' }),
])

const COMPARISON_POINTS = [
  { t: Date.parse('2026-10-01T00:00:00Z'), calculatedAt: '2026-10-01T00:00:00Z', OFFICIAL: 1, MOMENTUM: null, details: { OFFICIAL: { version: 1, status: 'CALCULATED' } } },
  { t: Date.parse('2026-10-02T00:00:00Z'), calculatedAt: '2026-10-02T00:00:00Z', OFFICIAL: null, MOMENTUM: 1.2, details: { MOMENTUM: { version: 1, status: 'NO_STATS' } } },
]

function renderChartWithContainer(props = {}) {
  return render(<QuoteChart points={POINTS} status="ready" strategy="OFFICIAL" range="month" league="PREMIER_LEAGUE" onStrategyChange={vi.fn()} onRangeChange={vi.fn()} onRetry={vi.fn()} {...props} />)
}

function renderChart(props = {}) {
  const handlers = { onStrategyChange: vi.fn(), onRangeChange: vi.fn(), onRetry: vi.fn() }
  render(
    <QuoteChart
      points={POINTS}
      status="ready"
      strategy="OFFICIAL"
      range="month"
      league="PREMIER_LEAGUE"
      {...handlers}
      {...props}
    />,
  )
  return handlers
}

function tableRows() {
  const table = screen.getByRole('table')
  return within(table).getAllByRole('row').slice(1).map((row) => within(row).getAllByRole('cell').map((cell) => plain(cell.textContent)))
}

afterEach(cleanup)

describe('QuoteChart', () => {
  it('compares market and alternative with legible names, Spanish statuses and absent points preserved', () => {
    const { container } = renderChartWithContainer({ comparisonCodes: ['OFFICIAL', 'MOMENTUM'], comparisonPoints: COMPARISON_POINTS })
    const table = screen.getByRole('table')
    expect(table.className).toBe('quote-chart__table')
    expect(within(table).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual(['Fecha', 'Oficial', 'Momentum'])
    expect(tableRows().map((row) => row.slice(1))).toEqual([['1,00 cr · Calculada', 'Sin cotización'], ['Sin cotización', '1,20 cr · Sin estadísticas']])
    expect(table.textContent).not.toMatch(/NO_STATS|v1 ·/)
    expect(screen.getByRole('img', { name: 'Oficial / Momentum · Último mes · 2 fechas' })).toBeTruthy()
    expect(screen.queryByRole('group', { name: 'Estrategia' })).toBeNull()
    expect(container.querySelector('.quote-chart__series--market .recharts-line-curve')).toBeTruthy()
    expect(container.querySelector('.quote-chart__series--alternative .recharts-line-curve')).toBeTruthy()
  })
  it('shows a legend with a color sample for each line', () => {
    renderChart({ comparisonCodes: ['MOMENTUM', 'OFFICIAL'], comparisonPoints: COMPARISON_POINTS })
    const items = within(screen.getByRole('list')).getAllByRole('listitem')
    expect(items.map((item) => item.textContent)).toEqual(['Momentum', 'Oficial'])
    expect(items.map((item) => item.querySelector('i').className)).toEqual(['quote-chart__swatch quote-chart__series--market', 'quote-chart__swatch quote-chart__series--alternative'])
  })
  it('offers retry when one comparison curve fails', () => {
    const handlers = renderChart({ comparisonCodes: ['OFFICIAL', 'MOMENTUM'], comparisonPoints: COMPARISON_POINTS.slice(0, 1), partialError: true })
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(handlers.onRetry).toHaveBeenCalledTimes(1)
  })
  it('renders an accessible table with the same chronological points as the chart', () => {
    renderChart()
    expect(within(screen.getByRole('table')).getAllByRole('columnheader').map((cell) => cell.textContent)).toEqual([
      'Fecha', 'Valor', 'Estrategia', 'Versión',
    ])
    expect(tableRows()).toEqual([
      [formatDate('2026-10-01T18:00:00Z'), '1,10 cr', 'Oficial', '1'],
      [formatDate('2026-10-08T18:00:00Z'), '1,20 cr', 'Oficial', '1'],
      [formatDate('2026-10-20T18:00:00Z'), '1,30 cr', 'Oficial', '2'],
    ].map((row) => row.map(plain)))
    expect(screen.getByRole('table').className).toContain('quote-chart__table')
  })

  it('describes the chart with strategy, range and number of quotes', () => {
    renderChart({ strategy: 'MOMENTUM', points: POINTS.map((point) => ({ ...point, strategy: 'MOMENTUM' })) })
    const caption = 'Momentum · Último mes · 3 cotizaciones'
    expect(screen.getByText(caption, { selector: 'figcaption' })).toBeTruthy()
    expect(screen.getByRole('img', { name: caption })).toBeTruthy()
    expect(tableRows().map((row) => row[2])).toEqual(['Momentum', 'Momentum', 'Momentum'])
  })

  it('includes the time when two points share a UTC day', () => {
    const points = toQuotePoints([quote('2026-10-01T01:00:00Z', 1), quote('2026-10-01T23:00:00Z', 1.1)])
    renderChart({ points })
    expect(tableRows().map((row) => row[0])).toEqual([
      plain(formatDateTime('2026-10-01T01:00:00Z')),
      plain(formatDateTime('2026-10-01T23:00:00Z')),
    ])
  })

  it('colors the line with the league token', () => {
    renderChart({ league: 'LIGUE_1' })
    expect(screen.getByRole('figure').style.getPropertyValue('--quote-line')).toBe('var(--liga-ligue1)')
  })

  it('falls back to the neutral token for an unknown league', () => {
    renderChart({ league: 'MLS' })
    expect(screen.getByRole('figure').style.getPropertyValue('--quote-line')).toBe('var(--tinta-2)')
  })

  it('offers native radio groups for strategy and range', () => {
    const handlers = renderChart()
    const strategy = screen.getByRole('group', { name: 'Estrategia' })
    const range = screen.getByRole('group', { name: 'Rango' })
    expect(within(strategy).getByRole('radio', { name: 'Oficial' }).checked).toBe(true)
    expect(within(range).getByRole('radio', { name: 'Último mes' }).checked).toBe(true)
    fireEvent.click(within(strategy).getByRole('radio', { name: 'Momentum' }))
    fireEvent.click(within(range).getByRole('radio', { name: 'Toda la temporada' }))
    expect(handlers.onStrategyChange).toHaveBeenCalledWith('MOMENTUM')
    expect(handlers.onRangeChange).toHaveBeenCalledWith('season')
  })

  it('shows the empty state while keeping the selectors enabled', () => {
    renderChart({ points: [], status: 'empty' })
    expect(screen.getByText('Todavía sin cotizaciones')).toBeTruthy()
    expect(screen.getByText('Sin datos')).toBeTruthy()
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.getAllByRole('radio').every((radio) => !radio.disabled)).toBe(true)
  })

  it('shows a section error with retry', () => {
    const handlers = renderChart({ points: [], status: 'error' })
    expect(screen.getByText('No pudimos cargar la evolución')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(handlers.onRetry).toHaveBeenCalled()
  })

  it('announces loading', () => {
    renderChart({ points: [], status: 'loading' })
    const busy = screen.getByLabelText('Cargando evolución…')
    expect(busy.getAttribute('aria-busy')).toBe('true')
  })
})

describe('QuoteTooltip', () => {
  it('shows date, value, strategy label and version of the active point', () => {
    render(<QuoteTooltip active payload={[{ payload: POINTS[2] }]} sameDay={false} />)
    expect(screen.getByText(formatDate('2026-10-20T18:00:00Z'))).toBeTruthy()
    expect(screen.getByText('1,30 cr')).toBeTruthy()
    expect(screen.getByText('Oficial')).toBeTruthy()
    expect(screen.getByText('Versión 2')).toBeTruthy()
  })

  it('includes the time when points share a day', () => {
    render(<QuoteTooltip active payload={[{ payload: POINTS[0] }]} sameDay />)
    expect(screen.getByText(formatDateTime('2026-10-01T18:00:00Z'))).toBeTruthy()
  })

  it('renders nothing when inactive', () => {
    const { container } = render(<QuoteTooltip active={false} payload={[{ payload: POINTS[0] }]} sameDay={false} />)
    expect(container.innerHTML).toBe('')
  })
})
