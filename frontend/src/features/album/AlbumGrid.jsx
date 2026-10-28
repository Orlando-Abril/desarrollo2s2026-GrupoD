import { Link } from 'react-router-dom'
import Figurita from '../../components/Figurita.jsx'
import './AlbumGrid.css'

export default function AlbumGrid({ players }) {
  return (
    <div className="figurita-grid">
      {players.map((player) => (
        <Link
          key={player.id}
          to={`/jugadores/${player.id}`}
          className="figurita-grid__link"
          aria-label={`Ver detalle de ${player.fullName}`}
        >
          <Figurita {...player} />
        </Link>
      ))}
    </div>
  )
}
