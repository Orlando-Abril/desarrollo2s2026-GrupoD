import Figurita from '../../components/Figurita.jsx'
import './AlbumGrid.css'

export default function AlbumGrid({ players }) {
  return (
    <div className="figurita-grid">
      {players.map((player) => <Figurita key={player.id} {...player} />)}
    </div>
  )
}
