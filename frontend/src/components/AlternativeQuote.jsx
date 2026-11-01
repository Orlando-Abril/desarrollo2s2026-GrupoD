import { differenceTone, formatSignedCredits, formatSignedPercent, quoteStatusLabel } from '../utils/comparison.js'
import { formatCredits } from '../utils/format.js'
import './AlternativeQuote.css'

export default function AlternativeQuote({ marketValue, quote, label }) {
  if (quote === null) return <span className="alternative-quote"><span className="alternative-quote__row">Sin cotización {label}</span></span>
  const difference = Number(quote.value) - Number(marketValue)
  const percent = Number(marketValue) > 0 ? difference / Number(marketValue) * 100 : null
  const calculated = !quote.status || quote.status === 'CALCULATED'
  return (
    <span className="alternative-quote">
      <span className="alternative-quote__row"><span>{label}</span><strong>{formatCredits(quote.value)}</strong></span>
      <span className="alternative-quote__row">
        <span>Diferencia</span>
        <strong className={`alternative-quote__difference alternative-quote__difference--${differenceTone(difference)}`}>
          <span>{formatSignedCredits(difference)}</span> <span>({formatSignedPercent(percent)})</span>
        </strong>
      </span>
      {calculated ? null : <span className="alternative-quote__row">Estado: {quoteStatusLabel(quote.status)}</span>}
    </span>
  )
}
