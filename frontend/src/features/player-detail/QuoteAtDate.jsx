import { useId, useState } from 'react'
import Button from '../../components/Button.jsx'
import Field from '../../components/Field.jsx'
import { formatCredits } from '../../utils/format.js'
import { STRATEGIES } from './quoteCatalog.js'
import { formatDate } from './quoteFormat.js'
import './QuoteAtDate.css'

function AtResult({ atQuery, onRetry }) {
  if (atQuery.status === 'loading') return <p>Consultando…</p>
  if (atQuery.status === 'empty') return <p>Sin cotización para esa fecha</p>
  if (atQuery.status === 'error') {
    return (
      <div className="quote-at-date__error">
        <p>No pudimos consultar la cotización.</p>
        <Button variant="secondary" type="button" onClick={onRetry}>Reintentar</Button>
      </div>
    )
  }
  if (atQuery.status !== 'found') return null
  const { quote } = atQuery
  return (
    <dl className="quote-at-date__found">
      <div><dt>Valor</dt><dd>{formatCredits(quote.value)}</dd></div>
      <div><dt>Cálculo</dt><dd>Calculada el {formatDate(quote.calculatedAt)}</dd></div>
      <div><dt>Estrategia</dt><dd>{STRATEGIES[quote.strategy]?.label ?? quote.strategy}</dd></div>
      <div><dt>Versión</dt><dd>{`Versión ${quote.version}`}</dd></div>
    </dl>
  )
}

export default function QuoteAtDate({ today, atQuery, onQuery, onRetry }) {
  const titleId = useId()
  const inputId = useId()
  const [date, setDate] = useState('')

  const submit = (event) => {
    event.preventDefault()
    onQuery(date)
  }

  return (
    <section className="quote-at-date" aria-labelledby={titleId}>
      <h2 id={titleId}>Cotización en una fecha</h2>
      <form className="quote-at-date__form" onSubmit={submit} noValidate>
        <Field
          id={inputId}
          label="Fecha"
          type="date"
          max={today}
          required
          value={date}
          error={atQuery.validationError}
          onChange={(event) => setDate(event.target.value)}
        />
        <Button type="submit">Consultar</Button>
      </form>
      <div className="quote-at-date__result" aria-live="polite" data-testid="at-result">
        <AtResult atQuery={atQuery} onRetry={onRetry} />
      </div>
    </section>
  )
}
