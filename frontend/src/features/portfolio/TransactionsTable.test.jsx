import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import TransactionsTable from './TransactionsTable.jsx'
import { transaction, transactionPage } from './portfolioTestData.js'

afterEach(cleanup)

describe('TransactionsTable', () => {
  it('renders transaction metadata, links and accessible pagination', () => {
    const change = vi.fn()
    const pageData = transactionPage({ items: [transaction({ id: 2, type: 'VENTA' })], page: 1, totalPages: 3, totalElements: 41, first: false, last: false })
    render(<MemoryRouter><TransactionsTable pageData={pageData} page={2} status="ready" onPageChange={change}/></MemoryRouter>)
    expect(screen.getByText('Venta')).toBeTruthy(); expect(screen.getByRole('link', { name: 'Alex Morgan' }).getAttribute('href')).toBe('/jugadores/42')
    expect(screen.getByText('1,2500 cr')).toBeTruthy(); expect(screen.getByText('3,7500 cr')).toBeTruthy()
    expect(screen.getByText('Página 2 de 3 · 41 operaciones')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Página anterior' })); fireEvent.click(screen.getByRole('button', { name: 'Página siguiente' }))
    expect(change.mock.calls).toEqual([[1], [3]])
  })
  it('disables controls while loading', () => {
    render(<MemoryRouter><TransactionsTable pageData={transactionPage({ first: false, last: false })} page={1} status="loading" onPageChange={() => {}}/></MemoryRouter>)
    expect(screen.getAllByRole('button').every((button) => button.disabled)).toBe(true)
  })
})
