import { Link } from 'react-router-dom'
import EstadoPanel from '../components/EstadoPanel.jsx'
import AllocationDonut from '../features/portfolio/AllocationDonut.jsx'
import PerformanceChart from '../features/portfolio/PerformanceChart.jsx'
import PositionsTable from '../features/portfolio/PositionsTable.jsx'
import TransactionsTable from '../features/portfolio/TransactionsTable.jsx'
import { formatCredits, formatResult, resultKind, resultLabel } from '../features/portfolio/portfolioFormat.js'
import usePortfolio from '../features/portfolio/usePortfolio.js'
import './PortfolioPage.css'

function Summary({ portfolio }) {
  const items = [
    ['Saldo', formatCredits(portfolio.balance)],
    ['Invertido', formatCredits(portfolio.totalCost)],
    ['Valor actual', formatCredits(portfolio.totalCurrentValue)],
    ['Resultado', formatResult(portfolio.totalProfitLoss)],
  ]
  return <><dl className="portfolio-summary">{items.map(([label, value], index) => <div key={label}><dt>{label}</dt><dd className={index === 3 ? `portfolio-result--${resultKind(portfolio.totalProfitLoss)}` : ''}>{index === 3 ? <span className="sr-only">{resultLabel(portfolio.totalProfitLoss)}: </span> : null}{value}</dd></div>)}</dl>
    <p className="portfolio-balance-detail">Disponible: {formatCredits(portfolio.availableBalance)} · Reservado: {formatCredits(portfolio.reservedCredits)}</p></>
}

function ResourceError({ title, onRetry, stale }) {
  return <div role="alert" className="portfolio-notice portfolio-notice--error"><strong>{title}</strong>{stale ? <span> Se muestran datos anteriores, que podrían estar desactualizados.</span> : null}<button type="button" onClick={onRetry}>Reintentar</button></div>
}

function IdentityLoading() {
  return <div className="portfolio-page" aria-busy="true"><h1>Mi portfolio</h1><output className="portfolio-loading-status">Cargando tu portfolio…</output></div>
}

function IdentityError({ onRetry }) {
  return <div className="portfolio-page"><h1>Mi portfolio</h1><EstadoPanel stamp="Error" stampVariant="danger" title="No pudimos identificar tu cuenta" action={{ label: 'Reintentar', onClick: onRetry }}>Probá nuevamente.</EstadoPanel></div>
}

function PortfolioContent({ portfolio }) {
  if (!portfolio.positions.length) {
    return <><Summary portfolio={portfolio}/><div className="portfolio-empty"><h3>Todavía no compraste tokens</h3><p>Explorá el álbum para conocer jugadores y empezar tu portfolio.</p><Link to="/album">Ir al álbum</Link></div></>
  }

  return <><Summary portfolio={portfolio}/><PositionsTable positions={portfolio.positions}/><div className="portfolio-charts"><PerformanceChart positions={portfolio.positions}/><AllocationDonut positions={portfolio.positions} totalCurrentValue={portfolio.totalCurrentValue}/></div></>
}

function PortfolioSection({ state }) {
  const hasPortfolio = Boolean(state.portfolio)
  const showLoading = state.portfolioStatus === 'loading' && !hasPortfolio

  return <section aria-labelledby="portfolio-position-title" aria-busy={state.portfolioStatus === 'loading' || state.portfolioRefreshing}><h2 id="portfolio-position-title">Posición actual</h2>
    {showLoading ? <output className="portfolio-loading-status">Cargando posición…</output> : null}
    {state.portfolioStatus === 'error' ? <ResourceError title="No pudimos cargar tu posición." onRetry={state.retryPortfolio} stale={hasPortfolio}/> : null}
    {hasPortfolio ? <PortfolioContent portfolio={state.portfolio}/> : null}
  </section>
}

function TransactionsSection({ state }) {
  const hasTransactions = Boolean(state.transactions?.items?.length)
  const showLoading = state.transactionsStatus === 'loading' && !hasTransactions
  const showEmpty = state.transactionsStatus === 'empty' && !hasTransactions
  const visibleStatus = state.transactionsStatus === 'error' ? 'ready' : state.transactionsStatus

  return <section aria-labelledby="portfolio-history-title" aria-busy={state.transactionsStatus === 'loading' || state.transactionsRefreshing}><h2 id="portfolio-history-title">Historial de operaciones</h2>
    {showLoading ? <output className="portfolio-loading-status">Cargando operaciones…</output> : null}
    {state.transactionsStatus === 'error' ? <ResourceError title="No pudimos cargar el historial." onRetry={state.retryTransactions} stale={hasTransactions}/> : null}
    {showEmpty ? <p>Todavía no tenés operaciones registradas.</p> : null}
    {hasTransactions ? <TransactionsTable pageData={state.transactions} page={state.page} status={visibleStatus} onPageChange={state.setPage}/> : null}
  </section>
}

export default function PortfolioPage() {
  const state = usePortfolio()
  if (state.identityStatus === 'loading') return <IdentityLoading/>
  if (state.identityStatus === 'error') return <IdentityError onRetry={state.retryIdentity}/>

  return <div className="portfolio-page"><header><p className="portfolio-page__eyebrow">Módulo 5</p><h1>Mi portfolio</h1><p>Tu posición actual, rendimiento e historial de operaciones.</p></header>
    <PortfolioSection state={state}/>
    <TransactionsSection state={state}/>
  </div>
}
