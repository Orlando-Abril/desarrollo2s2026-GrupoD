import { useId } from 'react'
import VariationBadge from '../../components/VariationBadge.jsx'
import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import { formatCredits } from '../../utils/format.js'
import './PlayerHeader.css'

export default function PlayerHeader({ player, quote, variation }) {
  const titleId = useId()
  const leagueData = LEAGUES[player.league] ?? { label: '—', colorVar: '--tinta-2' }
  const positionKey = Object.keys(POSITIONS).find((key) => player.positions.includes(key))
  const position = positionKey ? POSITIONS[positionKey] : null

  return (
    <section className="player-header" aria-labelledby={titleId} style={{ '--figurita-liga': `var(${leagueData.colorVar})` }}>
      <div className="player-header__photo">
        <span className="player-header__number"><small>N°</small>{String(player.id).padStart(3, '0')}</span>
        <span className="player-header__position" title={position?.label}>{position?.abbr ?? '—'}</span>
        <svg className="player-header__silhouette" viewBox="0 0 100 110" aria-hidden="true">
          <circle cx="50" cy="30" r="20" fill="currentColor" />
          <path d="M8 110c2-35 19-53 42-53s40 18 42 53H8Z" fill="currentColor" />
        </svg>
      </div>
      <div className="player-header__body">
        <h1 id={titleId} className="player-header__name">{player.fullName}</h1>
        <dl className="player-header__facts">
          <div><dt>Equipo</dt><dd>{player.team}</dd></div>
          <div><dt>Liga</dt><dd><i className="player-header__swatch" aria-hidden="true" />{leagueData.label}</dd></div>
          <div><dt>Nacionalidad · edad</dt><dd>{`${player.nationality ?? '—'} · ${player.age ?? '—'}`}</dd></div>
        </dl>
        <div className="player-header__quote">
          <span>Cotización actual</span>
          <strong data-testid="current-quote">{quote ? formatCredits(quote.value) : '—'}</strong>
          <VariationBadge current={variation.current} previous={variation.previous} />
        </div>
      </div>
    </section>
  )
}
