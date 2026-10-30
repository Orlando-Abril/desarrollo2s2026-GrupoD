import { useCallback, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import ConfirmDialog from '../components/ConfirmDialog.jsx'
import EstadoPanel from '../components/EstadoPanel.jsx'
import SummaryList from '../components/SummaryList.jsx'
import Toast from '../components/Toast.jsx'
import { hasMarketMessage, marketErrorMessage } from '../features/trading/marketErrors.js'
import MyOrdersList from '../features/trading/MyOrdersList.jsx'
import { formatPrice, sideLabel } from '../features/trading/tradeFormat.js'
import { priceToUnits } from '../features/trading/tradeMath.js'
import useMyOrders from '../features/trading/useMyOrders.js'
import { formatInteger } from '../utils/format.js'
import './MarketPage.css'

function cancelRows(order) {
  return [
    ['Jugador', order.playerName],
    ['Lado', sideLabel(order.side)],
    ['Pendiente', `${formatInteger(order.remainingQuantity)} tokens`],
    ['Precio', formatPrice(priceToUnits(order.limitPrice))],
  ]
}

export default function MarketPage() {
  const navigate = useNavigate()
  const market = useMyOrders()
  const [pending, setPending] = useState(null)
  const [toast, setToast] = useState(null)

  const dismissToast = useCallback(() => setToast(null), [])
  const closeDialog = useCallback(() => setPending(null), [])
  const confirmCancel = useCallback(async () => {
    const summary = await market.cancel(pending)
    setPending(null)
    if (summary) setToast(summary)
  }, [market, pending])

  let content
  if (market.status === 'loading') {
    content = <p className="market-page__loading" aria-busy="true">Cargando tus órdenes…</p>
  } else if (market.status === 'error') {
    const message = hasMarketMessage(market.loadError)
      ? marketErrorMessage(market.loadError)
      : 'No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.'
    content = (
      <EstadoPanel stamp="Sin conexión" stampVariant="error" title="No pudimos cargar tus órdenes" action={{ label: 'Reintentar', onClick: market.reload }}>
        {message}
      </EstadoPanel>
    )
  } else if (market.orders.length === 0) {
    content = (
      <EstadoPanel stamp="Sin órdenes" title="No tenés órdenes abiertas" action={{ label: 'Ir al álbum', onClick: () => navigate('/album') }}>
        Cuando dejes una orden con precio límite en el libro, la vas a ver acá. Operá desde la ficha de un jugador.
      </EstadoPanel>
    )
  } else {
    content = <MyOrdersList orders={market.orders} cancellingId={market.cancellingId} onCancel={setPending} />
  }

  return (
    <div className="page-content market-page">
      <header className="page-heading">
        <h1>Mercado</h1>
        <p>Tus órdenes abiertas</p>
      </header>
      {market.cancelError ? <p className="market-page__error" role="alert">{market.cancelError}</p> : null}
      <div aria-live="polite">{content}</div>
      <ConfirmDialog
        open={pending !== null}
        title="Cancelar orden"
        confirmLabel="Cancelar orden"
        onConfirm={confirmCancel}
        onCancel={closeDialog}
        busy={pending !== null && market.cancellingId === pending.id}
      >
        {pending ? <SummaryList rows={cancelRows(pending)} /> : null}
      </ConfirmDialog>
      {toast ? <Toast onDismiss={dismissToast}>{toast}</Toast> : null}
    </div>
  )
}
