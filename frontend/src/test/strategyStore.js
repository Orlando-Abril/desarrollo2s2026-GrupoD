import { createStrategyViewStore } from '../context/StrategyViewContext.jsx'
import { activeOfficial } from './strategyFixtures.js'

// Store con la configuración de estrategias ya cargada, como la deja el selector del header.
export function strategyStore({ viewStrategy = 'MARKET_CURRENT', activeConfig = activeOfficial } = {}) {
  return createStrategyViewStore({ viewStrategy, activeConfig, configStatus: 'ready' })
}
