import './AlternativeQuote.css'

const four = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 4, maximumFractionDigits: 4 })
const two = new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

export default function AlternativeQuote({ marketValue, quote }) {
  if (quote === null) return <span className="alternative-quote">Sin cotización Momentum</span>
  const difference = Number(quote.value) - Number(marketValue)
  const percent = Number(marketValue) > 0 ? difference / Number(marketValue) * 100 : null
  const sign = difference >= 0 ? '+' : ''
  return <span className="alternative-quote">
    <span>Mercado actual: {four.format(marketValue)} cr</span>
    <span>Momentum: {four.format(quote.value)} cr</span>
    <span>Diferencia: {sign}{four.format(difference)} cr ({percent === null ? 'No disponible' : `${sign}${two.format(percent)} %`})</span>
    {quote.status && quote.status !== 'CALCULATED' ? <span>Estado: {quote.status}</span> : null}
  </span>
}
