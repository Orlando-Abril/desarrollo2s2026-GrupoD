import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import { formatCredits, formatInteger } from '../../utils/format.js'
import VariationBadge from './VariationBadge.jsx'
import './RankingTable.css'

const HEADERS = ['Puesto', 'Jugador', 'Equipo', 'Liga', 'Posición', 'Cotización', 'Variación']

export default function RankingTable({ items }) {
  return (
    <div className="ranking-table-panel">
      <table className="ranking-table">
        <thead>
          <tr>
            {HEADERS.map((header) => <th key={header} scope="col">{header}</th>)}
          </tr>
        </thead>
        <tbody>
          {items.map((entry) => {
            const { player } = entry
            const league = LEAGUES[player.league]
            return (
              <tr key={player.id}>
                <td className="ranking-table__number" data-testid="ranking-position">{formatInteger(entry.rank)}</td>
                <td className="ranking-table__wrap"><strong>{player.name}</strong></td>
                <td className="ranking-table__wrap">{player.team}</td>
                <td>
                  <span className="ranking-table__league">
                    <i style={{ '--league-color': `var(${league.colorVar})` }} aria-hidden="true" />
                    {league.label}
                  </span>
                </td>
                <td>
                  <span className="ranking-table__positions">
                    {player.positions.map((position) => <span key={position}>{POSITIONS[position].label}</span>)}
                  </span>
                </td>
                <td className="ranking-table__number">{formatCredits(entry.value)}</td>
                <td><VariationBadge value={entry.percentageChange} /></td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
