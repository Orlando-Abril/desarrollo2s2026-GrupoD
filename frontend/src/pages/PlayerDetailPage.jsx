import { useEffect } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import Casillero from '../components/Casillero.jsx'
import EstadoPanel from '../components/EstadoPanel.jsx'
import PlayerHeader from '../features/player-detail/PlayerHeader.jsx'
import QuoteAtDate from '../features/player-detail/QuoteAtDate.jsx'
import QuoteChart from '../features/player-detail/QuoteChart.jsx'
import StatsPanel from '../features/player-detail/StatsPanel.jsx'
import usePlayerDetail from '../features/player-detail/usePlayerDetail.js'
import TradingSection from '../features/trading/TradingSection.jsx'
import './PlayerDetailPage.css'

function PlayerDetail({ id }) {
  const navigate = useNavigate()
  const page = usePlayerDetail(id)
  const fullName = page.detail?.player.fullName

  useEffect(() => {
    if (!fullName) return undefined
    const previousTitle = document.title
    document.title = `${fullName} · Figuritas`
    return () => {
      document.title = previousTitle
    }
  }, [fullName])

  let content
  if (page.status === 'notFound') {
    content = (
      <EstadoPanel stamp="404" title="Jugador inexistente" action={{ label: 'Volver al álbum', onClick: () => navigate('/album') }}>
        No encontramos un jugador con ese número. Buscalo en el álbum.
      </EstadoPanel>
    )
  } else if (page.status === 'error') {
    content = (
      <EstadoPanel stamp="Sin conexión" stampVariant="error" title="No pudimos cargar el jugador" action={{ label: 'Reintentar', onClick: page.retry }}>
        No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.
      </EstadoPanel>
    )
  } else if (page.status === 'ready') {
    const { player, stats, quote } = page.detail
    content = (
      <>
        <PlayerHeader player={player} quote={quote} variation={page.variation} />
        <TradingSection player={player} />
        <QuoteChart
          points={page.history.points}
          status={page.history.status}
          strategy={page.strategy}
          range={page.range}
          league={player.league}
          onStrategyChange={page.setStrategy}
          onRangeChange={page.setRange}
          onRetry={page.retryHistory}
        />
        <QuoteAtDate today={page.today} atQuery={page.atQuery} onQuery={page.queryAt} onRetry={page.retryAt} />
        <StatsPanel stats={stats} />
      </>
    )
  } else {
    content = (
      <div className="player-detail__loading" aria-busy="true" aria-label="Cargando jugador…">
        <Casillero variant="cargando">Cargando jugador…</Casillero>
      </div>
    )
  }

  return (
    <div className="page-content player-detail">
      <Link className="player-detail__back" to="/album">← Volver al álbum</Link>
      {content}
    </div>
  )
}

export default function PlayerDetailPage() {
  const { id } = useParams()
  return <PlayerDetail key={id} id={id} />
}
