import { Link } from 'react-router-dom'
import Button from '../../components/Button.jsx'
import { formatDateTime } from '../player-detail/quoteFormat.js'
import { formatInteger } from '../../utils/format.js'
import { formatPrice, sideLabel, statusLabel, typeLabel } from './tradeFormat.js'
import { priceToUnits } from './tradeMath.js'
import './MyOrdersList.css'

const COLUMNS = ['Jugador', 'Lado', 'Tipo', 'Precio', 'Cantidad', 'Ejecutado', 'Pendiente', 'Estado', 'Creada']

function cells(order) {
  return [
    <Link key="player" to={`/jugadores/${order.playerId}`}>{order.playerName}</Link>,
    sideLabel(order.side),
    typeLabel(order.type),
    order.limitPrice === null ? '—' : formatPrice(priceToUnits(order.limitPrice)),
    formatInteger(order.quantity),
    formatInteger(order.filledQuantity),
    formatInteger(order.remainingQuantity),
    statusLabel(order.status),
    formatDateTime(order.createdAt),
  ]
}

// Una sola tabla: desde 900 px se ve como tabla y debajo cada fila se apila como tarjeta (data-label).
export default function MyOrdersList({ orders, cancellingId, onCancel }) {
  return (
    <table className="my-orders">
      <caption className="my-orders__caption">Tus órdenes abiertas, de la más nueva a la más vieja</caption>
      <thead>
        <tr>
          {COLUMNS.map((column) => <th key={column} scope="col">{column}</th>)}
          <th scope="col"><span className="my-orders__sr">Acción</span></th>
        </tr>
      </thead>
      <tbody>
        {orders.map((order) => (
          <tr key={order.id} className={`my-orders__row my-orders__row--${order.side.toLowerCase()}`}>
            {cells(order).map((value, index) => (
              <td key={COLUMNS[index]} data-label={COLUMNS[index]}>{value}</td>
            ))}
            <td className="my-orders__action">
              <Button
                variant="secondary"
                type="button"
                disabled={cancellingId === order.id}
                aria-label={`Cancelar orden de ${sideLabel(order.side).toLowerCase()} de ${order.playerName}`}
                onClick={() => onCancel(order)}
              >
                Cancelar
              </Button>
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}
