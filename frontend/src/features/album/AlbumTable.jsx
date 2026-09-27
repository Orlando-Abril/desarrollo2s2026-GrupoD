import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import { formatCredits, formatInteger } from '../../utils/format.js'
import './AlbumTable.css'

export default function AlbumTable({ players }) {
  return (
    <div className="album-table-panel">
      <table className="album-table">
        <thead>
          <tr>{['N°', 'Jugador', 'Equipo', 'Liga', 'Posición', 'Nacionalidad', 'Edad', 'Valor'].map((label) => <th key={label}>{label}</th>)}</tr>
        </thead>
        <tbody>
          {players.map((player) => {
            const league = LEAGUES[player.league]
            return (
              <tr key={player.id}>
                <td className="album-table__number">{formatInteger(player.id)}</td>
                <td><span className="album-table__player"><i style={{ '--league-color': `var(${league.colorVar})` }} aria-hidden="true" /> <strong>{player.fullName}</strong></span></td>
                <td>{player.team}</td>
                <td>{league.label}</td>
                <td>{player.positions.length ? <span className="album-table__positions">{player.positions.map((position) => <span key={position}>{POSITIONS[position].label}</span>)}</span> : '—'}</td>
                <td>{player.nationality ?? '—'}</td>
                <td className="album-table__number">{player.age === null ? '—' : formatInteger(player.age)}</td>
                <td className="album-table__number">{formatCredits(player.marketValue)}</td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
