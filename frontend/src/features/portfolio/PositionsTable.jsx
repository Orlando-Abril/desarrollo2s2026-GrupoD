import { Link } from 'react-router-dom'
import { formatCredits, formatResult, resultKind, resultLabel } from './portfolioFormat.js'
import { quoteStatusLabel } from '../../utils/comparison.js'
import './PositionsTable.css'

export default function PositionsTable({ positions, comparisonLabel = null }) {
  const comparisonHeader = `${comparisonLabel} · valor y resultado`
  return <div className="portfolio-table-wrap"><table className="portfolio-table">
    <caption>Posiciones actuales</caption>
    <thead><tr><th scope="col">Jugador</th><th scope="col">Tokens</th><th scope="col">Precio promedio</th><th scope="col">Cotización</th><th scope="col">Valor actual</th><th scope="col">Ganancia/Pérdida</th>{comparisonLabel ? <th scope="col">{comparisonHeader}</th> : null}</tr></thead>
    <tbody>{positions.map((item) => <tr key={item.playerId}>
      <td data-label="Jugador"><Link to={`/jugadores/${item.playerId}`}>{item.playerName}</Link>{item.playerTeam ? <small>{item.playerTeam}</small> : null}</td>
      <td data-label="Tokens">{item.quantity}</td>
      <td data-label="Precio promedio">{formatCredits(item.averagePurchasePrice)}</td>
      <td data-label="Cotización">{formatCredits(item.marketValue)}</td>
      <td data-label="Valor actual">{formatCredits(item.currentValue)}</td>
      <td data-label="Ganancia/Pérdida" className={`portfolio-result portfolio-result--${resultKind(item.profitLoss)}`}><span className="sr-only">{resultLabel(item.profitLoss)}: </span>{formatResult(item.profitLoss)}</td>
      {comparisonLabel ? <td data-label={comparisonHeader}>{item.strategyValuation?.value == null || item.strategyValuation?.profitLoss == null ? 'No disponible · sin cotización' : <><strong>{formatCredits(item.strategyValuation.value)}</strong><span> · {formatResult(item.strategyValuation.profitLoss)}</span><small> · {quoteStatusLabel(item.strategyValuation.quoteStatus)}</small></>}</td> : null}
    </tr>)}</tbody>
  </table></div>
}
