import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const useRankingMock = vi.fn()

vi.mock('../features/ranking/useRanking.js', () => ({ default: useRankingMock }))

const { default: RankingPage } = await import('./RankingPage.jsx')

function renderPage() {
  return render(<MemoryRouter><RankingPage /></MemoryRouter>)
}

function entry(rank, id, percentageChange, overrides = {}) {
  const { player: playerOverrides = {}, ...entryOverrides } = overrides
  return {
    rank,
    player: {
      id,
      name: `Jugador ${id}`,
      team: `Equipo ${id}`,
      league: 'LA_LIGA',
      positions: ['FORWARD'],
      ...playerOverrides,
    },
    value: 1000 + id,
    percentageChange,
    strategy: 'OFFICIAL',
    version: 1,
    ...entryOverrides,
  }
}

function rankingState(overrides = {}) {
  const items = [
    entry(4, 4, 2.5),
    entry(2, 2, -1.25, { player: { positions: ['MIDFIELDER', 'FORWARD'] } }),
    entry(8, 8, 0),
    entry(1, 1, null),
  ]
  return {
    filters: { strategy: '', league: '', position: '' },
    changeFilter: vi.fn(),
    effectiveStrategy: 'OFFICIAL',
    status: 'ready',
    error: null,
    retry: vi.fn(),
    page: 1,
    setPage: vi.fn(),
    items,
    total: items.length,
    totalPages: 1,
    from: 1,
    to: items.length,
    pageWindow: [1],
    ...overrides,
  }
}

afterEach(cleanup)

describe('RankingPage', () => {
  beforeEach(() => {
    useRankingMock.mockReset().mockReturnValue(rankingState())
  })

  it('renders the server order, semantic columns and all ranking information', () => {
    renderPage()

    const headers = screen.getAllByRole('columnheader')
    expect(headers.map((cell) => cell.textContent)).toEqual([
      'Puesto', 'Jugador', 'Equipo', 'Liga', 'Posición', 'Cotización', 'Variación',
    ])
    expect(headers.every((cell) => cell.getAttribute('scope') === 'col')).toBe(true)
    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getByTestId('ranking-position').textContent)).toEqual(['4', '2', '8', '1'])
    expect(within(rows[0]).getByText('Jugador 4')).toBeTruthy()
    expect(within(rows[0]).getByText('Equipo 4')).toBeTruthy()
    expect(within(rows[0]).getByText('La Liga')).toBeTruthy()
    expect(within(rows[1]).getByText('Mediocampista')).toBeTruthy()
    expect(within(rows[1]).getByText('Delantero')).toBeTruthy()
    expect(within(rows[0]).getByText('1.004,00 cr')).toBeTruthy()
    expect(within(rows[0]).getByRole('img', { name: 'Subió 2,50 %' })).toBeTruthy()
    expect(within(rows[1]).getByRole('img', { name: 'Bajó 1,25 %' })).toBeTruthy()
    expect(within(rows[2]).getByRole('img', { name: 'Sin cambios' })).toBeTruthy()
    expect(within(rows[3]).getByRole('img', { name: 'Sin variación disponible' })).toBeTruthy()
  })

  it('links each player name to its detail', () => {
    renderPage()

    const rows = screen.getAllByRole('row').slice(1)
    expect(rows.map((row) => within(row).getByRole('link').getAttribute('href'))).toEqual([
      '/jugadores/4', '/jugadores/2', '/jugadores/8', '/jugadores/1',
    ])
    expect(within(rows[0]).getByRole('link', { name: 'Jugador 4' })).toBeTruthy()
    expect(within(rows[0]).getAllByRole('link')).toHaveLength(1)
  })

  it('keeps deliberately long names inside the local table wrapper', () => {
    const longText = 'Nombre extraordinariamente largo '.repeat(8).trim()
    useRankingMock.mockReturnValue(rankingState({
      items: [entry(1, 99, 1, {
        player: { name: longText, team: longText, league: 'PREMIER_LEAGUE' },
      })],
      total: 1,
      to: 1,
    }))

    const { container } = renderPage()

    expect(screen.getAllByText(longText)).toHaveLength(2)
    expect(container.querySelector('.ranking-page')).toBeTruthy()
    expect(container.querySelector('.ranking-table-panel')).toBeTruthy()
  })

  it('offers accessible strategy, league and position filters', () => {
    const state = rankingState()
    useRankingMock.mockReturnValue(state)
    renderPage()

    expect(screen.getByLabelText('Estrategia').selectedOptions[0].textContent).toBe('Activa (OFFICIAL)')
    fireEvent.change(screen.getByLabelText('Estrategia'), { target: { value: 'MOMENTUM' } })
    fireEvent.change(screen.getByLabelText('Liga'), { target: { value: 'SERIE_A' } })
    fireEvent.change(screen.getByLabelText('Posición'), { target: { value: 'DEFENDER' } })
    expect(state.changeFilter.mock.calls).toEqual([
      ['strategy', 'MOMENTUM'],
      ['league', 'SERIE_A'],
      ['position', 'DEFENDER'],
    ])
  })

  it('keeps the selected filters and does not invent an active strategy for an empty page', () => {
    useRankingMock.mockReturnValue(rankingState({
      filters: { strategy: '', league: 'LA_LIGA', position: 'FORWARD' },
      effectiveStrategy: null,
      items: [],
      total: 0,
      totalPages: 0,
      from: 0,
      to: 0,
      pageWindow: [],
    }))
    renderPage()

    expect(screen.getByLabelText('Estrategia').selectedOptions[0].textContent).toBe('Activa')
    expect(screen.getByLabelText('Liga').value).toBe('LA_LIGA')
    expect(screen.getByLabelText('Posición').value).toBe('FORWARD')
  })

  it('hides previous table and pager while loading but keeps filters enabled', () => {
    useRankingMock.mockReturnValue(rankingState({ status: 'loading' }))
    renderPage()

    expect(screen.getByText('Cargando ranking…').getAttribute('aria-busy')).toBe('true')
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).toBeNull()
    expect(screen.getByLabelText('Estrategia').disabled).toBe(false)
    expect(screen.getByLabelText('Liga').disabled).toBe(false)
    expect(screen.getByLabelText('Posición').disabled).toBe(false)
  })

  it('renders pager limits and requests the selected human page', () => {
    const state = rankingState({ total: 25, totalPages: 2, to: 20, pageWindow: [1, 2] })
    useRankingMock.mockReturnValue(state)
    const scrollIntoView = vi.fn()
    Element.prototype.scrollIntoView = scrollIntoView
    renderPage()

    expect(screen.getByRole('button', { name: 'Página anterior' }).disabled).toBe(true)
    expect(screen.getByRole('button', { name: 'Página siguiente' }).disabled).toBe(false)
    fireEvent.click(screen.getByRole('button', { name: 'Página siguiente' }))
    expect(state.setPage).toHaveBeenCalledWith(2)
    expect(scrollIntoView).toHaveBeenCalled()
  })

  it('shows a generic recoverable error without leaking details', () => {
    const state = rankingState({ status: 'error', error: new Error('database password') })
    useRankingMock.mockReturnValue(state)
    renderPage()

    expect(screen.getByText('No pudimos abrir el ranking')).toBeTruthy()
    expect(screen.queryByText(/database password/i)).toBeNull()
    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(state.retry).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('shows the exact empty state without a pager', () => {
    useRankingMock.mockReturnValue(rankingState({
      effectiveStrategy: null,
      items: [],
      total: 0,
      totalPages: 0,
      from: 0,
      to: 0,
      pageWindow: [],
    }))
    renderPage()

    expect(screen.getByText('Todavía no hay cotizaciones')).toBeTruthy()
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.queryByRole('navigation', { name: 'Paginación' })).toBeNull()
  })
})
