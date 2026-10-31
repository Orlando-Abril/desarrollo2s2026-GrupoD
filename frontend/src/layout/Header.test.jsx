import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { activeOfficial } from '../test/strategyFixtures.js'

const getActiveStrategy = vi.fn().mockResolvedValue(activeOfficial)
const changeActiveStrategy = vi.fn()
vi.mock('../api/strategyApi.js', () => ({ getActiveStrategy, changeActiveStrategy }))
vi.mock('../session/SessionContext.jsx', () => ({ useSession: () => ({ token: 'jwt', username: 'abril', isAuthenticated: true, logout: vi.fn() }) }))
const { StrategyViewProvider, createStrategyViewStore } = await import('../context/StrategyViewContext.jsx')
const { default: Header } = await import('./Header.jsx')

afterEach(cleanup)

const items = [
  { id: 'album', ruta: '/album', numero: '01', label: 'Álbum', habilitado: true },
  { id: 'portfolio', ruta: '/portfolio', numero: '02', label: 'Portfolio', habilitado: true },
  { id: 'ranking', ruta: '/ranking', numero: '03', label: 'Ranking', habilitado: true },
]

describe('Header strategy selector', () => {
  it('appears on album, detail and portfolio, keeps choice, and does not PUT', async () => {
    const store = createStrategyViewStore()
    render(<MemoryRouter initialEntries={['/album']}><StrategyViewProvider store={store}><Header items={items} /><Routes><Route path="/album" element={<a href="/jugadores/7">Detalle</a>} /><Route path="/portfolio" element={<p>Portfolio</p>} /><Route path="/ranking" element={<p>Ranking</p>} /></Routes></StrategyViewProvider></MemoryRouter>)
    await waitFor(() => expect(screen.getByRole('combobox', { name: /Ver con/i })).toBeTruthy())
    fireEvent.change(screen.getByRole('combobox', { name: /Ver con/i }), { target: { value: 'MOMENTUM' } })
    fireEvent.click(screen.getByRole('link', { name: /Portfolio/i }))
    expect(screen.getByRole('combobox', { name: /Ver con/i }).value).toBe('MOMENTUM')
    fireEvent.click(screen.getByRole('link', { name: /Ranking/i }))
    expect(screen.queryByRole('combobox', { name: /Ver con/i })).toBeNull()
    expect(changeActiveStrategy).not.toHaveBeenCalled()
  })
})
