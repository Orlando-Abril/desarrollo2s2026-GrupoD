import { useId } from 'react'
import EstadoPanel from '../../components/EstadoPanel.jsx'
import { formatInteger } from '../../utils/format.js'
import { formatPrice, formatPriceNumber } from './tradeFormat.js'
import { priceToUnits } from './tradeMath.js'
import './OrderBook.css'

function SideRows({ levels, side, empty }) {
  if (levels.length === 0) {
    return (
      <tr className="order-book__empty">
        <td colSpan={3}>{empty}</td>
      </tr>
    )
  }
  const label = side === 'SELL' ? 'Venta' : 'Compra'
  return levels.map((level) => (
    <tr key={`${side}-${level.price}`} className={`order-book__row order-book__row--${side.toLowerCase()}`}>
      <th scope="row">{label}</th>
      <td>{formatPriceNumber(priceToUnits(level.price))}</td>
      <td>{formatInteger(level.quantity)}</td>
    </tr>
  ))
}

function BookContent({ playerName, status, book, onRetry }) {
  if (status === 'error') {
    return (
      <EstadoPanel stamp="Sin conexión" stampVariant="error" title="No pudimos cargar el libro" action={{ label: 'Reintentar', onClick: onRetry }}>
        No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.
      </EstadoPanel>
    )
  }
  if (!book) return <p className="order-book__loading" aria-busy="true">Cargando libro…</p>
  const hasValue = book.marketValue !== null && book.marketValue !== undefined
  return (
    <>
      <p className="order-book__value">
        Cotización vigente: <strong>{hasValue ? formatPrice(priceToUnits(book.marketValue)) : '—'}</strong>
      </p>
      <div className="order-book__scroll">
        <table className="order-book__table">
          <caption>Libro de órdenes de {playerName}</caption>
          <thead>
            <tr>
              <th scope="col">Lado</th>
              <th scope="col">Precio (cr)</th>
              <th scope="col">Tokens</th>
            </tr>
          </thead>
          <tbody>
            <SideRows levels={book.sells} side="SELL" empty="Sin órdenes de venta" />
            <SideRows levels={book.buys} side="BUY" empty="Sin órdenes de compra" />
          </tbody>
        </table>
      </div>
    </>
  )
}

export default function OrderBook(props) {
  const titleId = useId()
  return (
    <section className="order-book" aria-labelledby={titleId}>
      <h3 id={titleId}>Libro de órdenes</h3>
      <BookContent {...props} />
    </section>
  )
}
