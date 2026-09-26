import Figurita from './Figurita.jsx'
import './AuthShowcase.css'

const PLAYERS = [
  { id: 14, fullName: 'Nico Centella', team: 'Puerto Norte', league: 'PREMIER_LEAGUE', positions: ['FORWARD'], nationality: 'Uruguay', age: 23, marketValue: 78.4 },
  { id: 32, fullName: 'Ivo Muralla', team: 'Real Sendero', league: 'LA_LIGA', positions: ['DEFENDER'], nationality: 'Argentina', age: 27, marketValue: 64.2 },
  { id: 8, fullName: 'Teo Brújula', team: 'Atlético Prisma', league: 'SERIE_A', positions: ['MIDFIELDER'], nationality: 'Chile', age: 25, marketValue: 91.75 },
]

export default function AuthShowcase({ before, highlight, after, subtitle }) {
  return (
    <aside className="auth-showcase" aria-hidden="true">
      <p className="auth-showcase__claim">{before} <em>{highlight}</em>{after ? ` ${after}` : ''}</p>
      <p className="auth-showcase__subtitle">{subtitle}</p>
      <div className="auth-showcase__cards">
        {PLAYERS.map((player) => <Figurita key={player.id} {...player} />)}
      </div>
    </aside>
  )
}
