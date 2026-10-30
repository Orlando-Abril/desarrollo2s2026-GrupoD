import { useCallback, useId, useState } from 'react'
import Toast from '../../components/Toast.jsx'
import OrderBook from './OrderBook.jsx'
import TradePanel from './TradePanel.jsx'
import { summarizeOrder } from './tradeMath.js'
import useOrderBook from './useOrderBook.js'
import './TradingSection.css'

export default function TradingSection({ player }) {
  const titleId = useId()
  const { status, book, reload } = useOrderBook(player.id)
  const [toast, setToast] = useState(null)

  const dismissToast = useCallback(() => setToast(null), [])
  const handleExecuted = useCallback((order) => {
    setToast(summarizeOrder(order))
    reload()
  }, [reload])

  return (
    <section className="trading" aria-labelledby={titleId}>
      <h2 id={titleId}>Operar</h2>
      <div className="trading__grid">
        <TradePanel player={player} bookStatus={status} book={book} onExecuted={handleExecuted} onStale={reload} />
        <OrderBook playerName={player.fullName} status={status} book={book} onRetry={reload} />
      </div>
      {toast ? <Toast onDismiss={dismissToast}>{toast}</Toast> : null}
    </section>
  )
}
