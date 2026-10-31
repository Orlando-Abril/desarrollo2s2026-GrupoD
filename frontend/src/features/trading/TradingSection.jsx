import { useCallback, useEffect, useId, useRef, useState } from 'react'
import Toast from '../../components/Toast.jsx'
import OrderBook from './OrderBook.jsx'
import TradePanel from './TradePanel.jsx'
import { summarizeOrder } from './tradeMath.js'
import useOrderBook from './useOrderBook.js'
import './TradingSection.css'

export default function TradingSection({ player, hash = '' }) {
  const titleId = useId()
  const { status, book, reload } = useOrderBook(player.id)
  const [toast, setToast] = useState(null)
  const headingRef = useRef(null)
  useEffect(() => {
    if (hash === '#operar') {
      headingRef.current?.scrollIntoView?.({ block: 'start' })
      headingRef.current?.focus()
    }
  }, [hash])

  const dismissToast = useCallback(() => setToast(null), [])
  const handleExecuted = useCallback((order) => {
    setToast(summarizeOrder(order))
    reload()
  }, [reload])

  return (
    <section className="trading" id="operar" aria-labelledby={titleId}>
      <h2 id={titleId} tabIndex={-1} ref={headingRef}>Operar</h2>
      <div className="trading__grid">
        <TradePanel player={player} bookStatus={status} book={book} onExecuted={handleExecuted} onStale={reload} />
        <OrderBook playerName={player.fullName} status={status} book={book} onRetry={reload} />
      </div>
      {toast ? <Toast onDismiss={dismissToast}>{toast}</Toast> : null}
    </section>
  )
}
