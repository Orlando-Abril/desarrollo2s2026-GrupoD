import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import OrderBook from './OrderBook.jsx'

const plain = (text) => text.replace(/\s/g, ' ')
const BOOK = {
  playerId: 42,
  marketValue: 1.2,
  sells: [{ price: 1.1, quantity: 7 }, { price: 1.2, quantity: 50 }],
  buys: [{ price: 1.05, quantity: 5 }],
}

function rows() {
  const table = screen.getByRole('table')
  return within(table).getAllByRole('row').slice(1).map((row) => plain(row.textContent))
}

describe('OrderBook', () => {
  afterEach(cleanup)

  it('renders an accessible table with sells ascending and then buys descending', () => {
    render(<OrderBook playerName="Jugador Test" status="ready" book={BOOK} onRetry={vi.fn()} />)

    const table = screen.getByRole('table')
    expect(table.querySelector('caption').textContent).toBe('Libro de órdenes de Jugador Test')
    const headers = within(table).getAllByRole('columnheader').map((cell) => cell.textContent)
    expect(headers).toEqual(['Lado', 'Precio (cr)', 'Tokens'])
    expect(rows()).toEqual(['Venta1,10007', 'Venta1,200050', 'Compra1,05005'])
  })

  it('shows the current market value', () => {
    render(<OrderBook playerName="Jugador Test" status="ready" book={BOOK} onRetry={vi.fn()} />)

    expect(plain(screen.getByText(/Cotización vigente/).textContent)).toContain('1,2000 cr')
  })

  it('shows each empty side', () => {
    render(<OrderBook playerName="Jugador Test" status="ready" book={{ ...BOOK, sells: [], buys: [] }} onRetry={vi.fn()} />)

    expect(rows()).toEqual(['Sin órdenes de venta', 'Sin órdenes de compra'])
  })

  it('is busy while loading', () => {
    render(<OrderBook playerName="Jugador Test" status="loading" book={null} onRetry={vi.fn()} />)

    const loading = screen.getByText('Cargando libro…')
    expect(loading.getAttribute('aria-busy')).toBe('true')
  })

  it('offers a retry on error', () => {
    const onRetry = vi.fn()
    render(<OrderBook playerName="Jugador Test" status="error" book={null} onRetry={onRetry} />)

    fireEvent.click(screen.getByRole('button', { name: 'Reintentar' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })
})
