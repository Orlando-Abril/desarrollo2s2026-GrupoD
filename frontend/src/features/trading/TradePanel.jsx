import { useId, useRef } from 'react'
import Button from '../../components/Button.jsx'
import ConfirmDialog from '../../components/ConfirmDialog.jsx'
import Field from '../../components/Field.jsx'
import SummaryList from '../../components/SummaryList.jsx'
import { formatInteger } from '../../utils/format.js'
import { formatPrice, sideLabel, typeLabel } from './tradeFormat.js'
import { limitPriceUnits } from './tradeMath.js'
import useTrade from './useTrade.js'
import './TradePanel.css'

function disabledReason(bookStatus, book) {
  if (bookStatus === 'error') return 'No pudimos cargar el libro del jugador.'
  if (bookStatus !== 'ready' || !book) return 'Esperando el libro del jugador…'
  if (book.marketValue === null || book.marketValue === undefined) return 'Este jugador todavía no tiene cotización'
  return null
}

function marketNotice(estimate, side) {
  if (estimate.fillable === 0) {
    return `No hay ${side === 'BUY' ? 'vendedores' : 'compradores'}: la orden se cancelaría sin ejecutarse`
  }
  if (estimate.unfilled > 0) {
    return `Sólo hay ${formatInteger(estimate.fillable)} tokens del otro lado; los ${formatInteger(estimate.unfilled)} restantes se cancelarían`
  }
  return null
}

function EstimateText({ estimate, side }) {
  if (!estimate) return <>Completá la cantidad para ver el total estimado.</>
  const notice = estimate.kind === 'market' ? marketNotice(estimate, side) : null
  if (notice && estimate.fillable === 0) return <>{notice}</>
  return (
    <>
      <span>Total estimado: <strong>{formatPrice(estimate.total)}</strong></span>
      {notice ? <span className="trade-panel__notice">{notice}</span> : null}
    </>
  )
}

function OrderSummary({ draft, playerName, estimate }) {
  const rows = [
    ['Operación', sideLabel(draft.side)],
    ['Tipo', typeLabel(draft.type)],
    ['Jugador', playerName],
    ['Cantidad', `${formatInteger(Number(draft.quantity))} tokens`],
  ]
  if (draft.type === 'LIMIT') rows.push(['Precio límite', formatPrice(limitPriceUnits(draft.limitPrice))])
  rows.push(['Total estimado', <EstimateText key="estimate" estimate={estimate} side={draft.side} />])
  return <SummaryList rows={rows} />
}

export default function TradePanel({ player, bookStatus, book, onExecuted, onStale }) {
  const id = useId()
  const quantityRef = useRef(null)
  const priceRef = useRef(null)
  const trade = useTrade({ playerId: player.id, book, onExecuted, onStale })
  const { draft, errors, band, estimate, phase } = trade
  const reason = disabledReason(bookStatus, book)
  const isBuy = draft.side === 'BUY'
  const verb = isBuy ? 'compra' : 'venta'

  const handleSubmit = (event) => {
    event.preventDefault()
    const invalid = trade.submit()
    if (invalid.quantity) quantityRef.current?.focus()
    else if (invalid.limitPrice) priceRef.current?.focus()
  }

  return (
    <section className="trade-panel" aria-labelledby={`${id}-title`}>
      <h3 id={`${id}-title`}>Comprar o vender</h3>
      {reason ? <p className="trade-panel__disabled">{reason}</p> : null}
      <form noValidate onSubmit={handleSubmit}>
        <fieldset className="trade-panel__fields" disabled={Boolean(reason) || phase === 'sending'}>
          <fieldset className="trade-panel__sides" aria-label="Lado de la orden">
            {['BUY', 'SELL'].map((side) => (
              <button
                key={side}
                type="button"
                className={`trade-panel__side trade-panel__side--${side.toLowerCase()}`}
                aria-pressed={draft.side === side}
                onClick={() => trade.setField('side', side)}
              >
                {side === 'BUY' ? 'Comprar' : 'Vender'}
              </button>
            ))}
          </fieldset>
          <fieldset className="trade-panel__types">
            <legend>Tipo de orden</legend>
            {[['MARKET', 'Al mercado (MARKET)'], ['LIMIT', 'Con precio límite (LIMIT)']].map(([value, label]) => (
              <label key={value}>
                <input
                  type="radio"
                  name={`${id}-type`}
                  value={value}
                  checked={draft.type === value}
                  onChange={() => trade.setField('type', value)}
                />
                {label}
              </label>
            ))}
          </fieldset>
          <Field
            ref={quantityRef}
            id={`${id}-quantity`}
            label="Cantidad de tokens"
            inputMode="numeric"
            autoComplete="off"
            value={draft.quantity}
            error={errors.quantity}
            onChange={(event) => trade.setField('quantity', event.target.value)}
            onBlur={() => trade.touchField('quantity')}
          />
          {draft.type === 'LIMIT' ? (
            <Field
              ref={priceRef}
              id={`${id}-price`}
              label="Precio límite (cr)"
              inputMode="decimal"
              autoComplete="off"
              value={draft.limitPrice}
              error={errors.limitPrice}
              hint={band ? `Rango permitido: ${formatPrice(band.min)} – ${formatPrice(band.max)}` : undefined}
              onChange={(event) => trade.setField('limitPrice', event.target.value)}
              onBlur={() => trade.touchField('limitPrice')}
            />
          ) : null}
          <p className="trade-panel__estimate" aria-live="polite" data-testid="trade-estimate">
            <EstimateText estimate={estimate} side={draft.side} />
          </p>
          <Button type="submit">{`Revisar ${verb}`}</Button>
        </fieldset>
      </form>
      {trade.submitError ? <p className="trade-panel__error" role="alert">{trade.submitError}</p> : null}
      <p className="trade-panel__result" aria-live="polite" data-testid="trade-result">{trade.lastResult}</p>
      <ConfirmDialog
        open={phase === 'confirming' || phase === 'sending'}
        title="Revisá tu orden"
        confirmLabel={`Confirmar ${verb}`}
        onConfirm={trade.confirm}
        onCancel={trade.cancelConfirm}
        busy={phase === 'sending'}
      >
        <OrderSummary draft={draft} playerName={player.fullName} estimate={estimate} />
      </ConfirmDialog>
    </section>
  )
}
