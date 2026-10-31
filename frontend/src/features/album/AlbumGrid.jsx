import { Link } from 'react-router-dom'
import Figurita from '../../components/Figurita.jsx'
import './AlbumGrid.css'

export default function AlbumGrid({ players, alternativeQuotes, alternativeLabel }) {
  return (
    <div className="figurita-grid">
      {players.map((player) => (
        <Link
          key={player.id}
          to={`/jugadores/${player.id}`}
          className="figurita-grid__link"
          aria-label={`Ver detalle de ${player.fullName}`}
        >
          <Figurita {...player} alternativeQuote={alternativeQuotes ? { label: alternativeLabel, quote: alternativeQuotes[player.id] ?? null } : undefined} />
        </Link>
      ))}
    </div>
  )
}
