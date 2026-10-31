import { Link } from 'react-router-dom'
import { formatCredits, formatDateTime } from './portfolioFormat.js'
import './TransactionsTable.css'

export default function TransactionsTable({ pageData, page, status, onPageChange }) {
  const loading = status === 'loading'
  const totalPages = pageData.totalPages || 1
  return <div><div className="portfolio-table-wrap"><table className="portfolio-table transactions-table"><caption>Operaciones ejecutadas</caption><thead><tr><th scope="col">Tipo</th><th scope="col">Jugador</th><th scope="col">Cantidad</th><th scope="col">Precio unitario</th><th scope="col">Total</th><th scope="col">Fecha y hora</th></tr></thead><tbody>{pageData.items.map((item) => <tr key={item.id}><td data-label="Tipo">{item.type === 'COMPRA' ? 'Compra' : 'Venta'}</td><td data-label="Jugador"><Link to={`/jugadores/${item.playerId}`}>{item.playerName}</Link></td><td data-label="Cantidad">{item.quantity}</td><td data-label="Precio unitario">{formatCredits(item.price)}</td><td data-label="Total">{formatCredits(item.total)}</td><td data-label="Fecha y hora"><time dateTime={item.executedAt}>{formatDateTime(item.executedAt)}</time></td></tr>)}</tbody></table></div>
    <nav className="transactions-pager" aria-label="Paginación del historial"><button type="button" disabled={loading || pageData.first || !pageData.items.length} onClick={() => onPageChange(page - 1)}>Página anterior</button><p aria-live="polite">Página {page} de {totalPages} · {pageData.totalElements} operaciones</p><button type="button" disabled={loading || pageData.last || !pageData.items.length} onClick={() => onPageChange(page + 1)}>Página siguiente</button></nav>
  </div>
}
