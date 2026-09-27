import { LEAGUES, POSITIONS } from '../domain/catalogo.js'
import { formatCredits } from '../utils/format.js'
import './Figurita.css'

export default function Figurita({ id, fullName, team, league, positions = [], nationality, age, marketValue }) {
  const leagueData = LEAGUES[league] ?? { label: '—', colorVar: '--tinta-2' }
  const positionKey = Object.keys(POSITIONS).find((key) => positions.includes(key))
  const position = positionKey ? POSITIONS[positionKey].abbr : '—'
  const metadata = `${nationality ?? '—'} · ${age ?? '—'}`

  return (
    <article className="figurita" aria-label={`${fullName}, ${team}`} style={{ '--figurita-liga': `var(${leagueData.colorVar})` }}>
      <div className="figurita__photo">
        <span className="figurita__number"><small>N°</small>{String(id).padStart(3, '0')}</span>
        <span className="figurita__position">{position}</span>
        <svg className="figurita__silhouette" viewBox="0 0 100 110" aria-hidden="true">
          <circle cx="50" cy="30" r="20" fill="currentColor" />
          <path d="M8 110c2-35 19-53 42-53s40 18 42 53H8Z" fill="currentColor" />
        </svg>
      </div>
      <h3 className="figurita__name" title={fullName}>{fullName}</h3>
      <div className="figurita__detail"><span>{team}</span><span>{leagueData.label}</span></div>
      <div className="figurita__value"><strong>{formatCredits(marketValue)}</strong><span>{metadata}</span></div>
    </article>
  )
}
