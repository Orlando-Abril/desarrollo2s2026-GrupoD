import Button from '../../components/Button.jsx'
import Toast from '../../components/Toast.jsx'
import useRecalculate from './useRecalculate.js'
import './RecalculateButton.css'

const CONFIRMATION = 'Se recalcularán todas las cotizaciones. ¿Querés continuar?'

const ERROR_MESSAGES = {
  in_progress: 'Ya hay un recálculo en curso',
  forbidden: 'No tenés permisos para recalcular cotizaciones.',
  network_error: 'No se pudo conectar con el servidor. Intentá de nuevo.',
  unexpected_error: 'No se pudo recalcular las cotizaciones. Intentá de nuevo.',
}

function SuccessSummary({ summary, onClose }) {
  return (
    <div className="recalculate-summary">
      <div className="recalculate-summary__heading">
        <strong>Recálculo completado</strong>
        <Button type="button" variant="secondary" onClick={onClose} aria-label="Cerrar resumen">Cerrar</Button>
      </div>
      <div className="recalculate-summary__body">
        <p className="recalculate-summary__run">Run ID: {summary.runId}</p>
        <section>
          <h2>Estrategias calculadas</h2>
          {summary.strategies.length === 0 ? <p>Ninguna</p> : (
            <ul>{summary.strategies.map((strategy) => (
              <li key={`${strategy.code}-${strategy.version}`}>
                <strong>{strategy.code} v{strategy.version}</strong>
                <span>CALCULATED: {strategy.calculated}</span>
                <span>NO_STATS: {strategy.noStats}</span>
                <span>INSUFFICIENT_HISTORY: {strategy.insufficientHistory}</span>
              </li>
            ))}</ul>
          )}
        </section>
        <section>
          <h2>Estrategias salteadas</h2>
          {summary.skippedStrategies.length === 0 ? <p>Ninguna</p> : (
            <ul>{summary.skippedStrategies.map((strategy) => (
              <li key={`${strategy.code}-${strategy.version}`}>
                {strategy.code} v{strategy.version}: {strategy.reason}
              </li>
            ))}</ul>
          )}
        </section>
        <p>Jugadores procesados: {summary.playersProcessed}</p>
        <p>Jugadores fallidos: {summary.playersFailed}</p>
        <p>Estrategia activa: {summary.activeStrategy}</p>
        <p>Valor de mercado actualizado: {summary.marketValueUpdated ? 'Sí' : 'No'}</p>
      </div>
    </div>
  )
}

export default function RecalculateButton({ onRecalculated }) {
  const { running, notification, forbidden, recalculate, dismissNotification } = useRecalculate({ onRecalculated })

  const handleClick = () => {
    if (!window.confirm(CONFIRMATION)) return
    void recalculate()
  }

  return (
    <div className="recalculate-control">
      {!forbidden ? (
        <Button type="button" onClick={handleClick} disabled={running} aria-busy={running ? 'true' : 'false'}>
          {running ? 'Recalculando…' : 'Recalcular cotizaciones'}
        </Button>
      ) : null}
      {notification?.type === 'success' ? (
        <Toast><SuccessSummary summary={notification.summary} onClose={dismissNotification} /></Toast>
      ) : null}
      {notification && notification.type !== 'success' ? (
        <Toast onDismiss={dismissNotification}>{ERROR_MESSAGES[notification.type]}</Toast>
      ) : null}
    </div>
  )
}
