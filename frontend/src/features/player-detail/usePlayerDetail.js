import { useCallback, useEffect, useState } from 'react'
import { getPlayerDetail } from '../../api/playerDetailApi.js'
import { ApiError } from '../../api/httpClient.js'
import { getPlayerQuotes } from '../../api/quotesApi.js'
import { useStrategyView } from '../../context/StrategyViewContext.jsx'
import { compareQuoteSeries } from './compareQuoteSeries.js'
import { STRATEGIES } from './quoteCatalog.js'
import { findPrevious, parsePlayerId, rangeToDates, todayUtc, toQuotePoints } from './quoteUtils.js'

const LOADING_HISTORY = { status: 'loading', points: [] }
const IDLE_AT = { status: 'idle', quote: null, date: '', validationError: null }
const INVALID_DATE = 'Elegí una fecha de hoy o anterior'

// Un 401 no tiene estado propio: el cliente HTTP cierra la sesión y la redirección desmonta la página.
function detailErrorStatus(error) {
  if (!(error instanceof ApiError)) return 'error'
  if (error.status === 400 || error.status === 404) return 'notFound'
  if (error.status === 401) return null
  return 'error'
}

function initialStrategy(quote) {
  return quote && STRATEGIES[quote.strategy] ? quote.strategy : 'OFFICIAL'
}

export default function usePlayerDetail(id, { now = new Date() } = {}) {
  const { comparisonStrategy, activeConfig } = useStrategyView()
  const playerId = parsePlayerId(id)
  // Se trabaja con el día UTC y no con el Date: así el efecto no se repite en cada render.
  const today = todayUtc(now)
  const [state, setState] = useState({ status: playerId ? 'loading' : 'notFound', detail: null })
  const [retryKey, setRetryKey] = useState(0)
  const [previousValue, setPreviousValue] = useState(null)
  const [selection, setSelection] = useState({ strategy: null, range: 'month' })
  const [history, setHistory] = useState(LOADING_HISTORY)
  const [historyRetryKey, setHistoryRetryKey] = useState(0)
  const [atQuery, setAtQuery] = useState(IDLE_AT)
  const [atRequest, setAtRequest] = useState(null)

  useEffect(() => {
    if (!playerId) return undefined
    const controller = new AbortController()

    getPlayerDetail(playerId, { signal: controller.signal })
      .then((detail) => {
        if (!controller.signal.aborted) setState({ status: 'ready', detail })
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        const status = detailErrorStatus(error)
        if (status) setState({ status, detail: null })
      })

    return () => controller.abort()
  }, [playerId, retryKey])

  const quote = state.detail?.quote ?? null
  const detailReady = state.status === 'ready'
  const strategy = selection.strategy ?? activeConfig?.marketStrategy?.code ?? initialStrategy(quote)
  const { range } = selection
  // Compara el precio de mercado vigente con la alternativa informada por GET /strategies/active.
  const marketCode = activeConfig?.marketStrategy?.code ?? quote?.strategy
  const comparisonKey = comparisonStrategy ? [...new Set([marketCode, comparisonStrategy].filter(Boolean))].join(',') : ''

  // La variación es secundaria: si su historial falla, la cabecera muestra "—" sin error.
  useEffect(() => {
    if (!quote) return undefined
    const controller = new AbortController()

    getPlayerQuotes(playerId, { strategy: quote.strategy }, { signal: controller.signal })
      .then((quotes) => {
        if (!controller.signal.aborted) setPreviousValue(findPrevious(quotes, quote)?.value ?? null)
      })
      .catch(() => {})

    return () => controller.abort()
  }, [playerId, quote])

  useEffect(() => {
    if (!detailReady) return undefined
    const controller = new AbortController()
    const dates = rangeToDates(range, new Date(`${today}T00:00:00Z`))

    if (comparisonKey) {
      const codes = comparisonKey.split(',')
      setHistory(LOADING_HISTORY)
      void Promise.allSettled(codes.map((code) => getPlayerQuotes(playerId, { strategy: code, ...dates }, { signal: controller.signal })))
        .then((results) => {
          if (controller.signal.aborted) return
          if (results.every((result) => result.status === 'rejected')) {
            setHistory({ status: 'error', points: [], comparisonPoints: [] })
            return
          }
          const comparisonPoints = compareQuoteSeries(codes.map((code, index) => [code, results[index].status === 'fulfilled' ? results[index].value : []]))
          setHistory({ status: comparisonPoints.length ? 'ready' : 'empty', points: [], comparisonPoints, partialError: results.some((result) => result.status === 'rejected') })
        })
      return () => controller.abort()
    }

    getPlayerQuotes(playerId, { strategy, ...dates }, { signal: controller.signal })
      .then((quotes) => {
        if (controller.signal.aborted) return
        const points = toQuotePoints(quotes)
        setHistory({ status: points.length ? 'ready' : 'empty', points })
      })
      .catch(() => {
        if (!controller.signal.aborted) setHistory({ status: 'error', points: [] })
      })

    return () => controller.abort()
  }, [detailReady, playerId, strategy, range, today, historyRetryKey, comparisonKey])

  useEffect(() => {
    if (!atRequest) return undefined
    const controller = new AbortController()

    getPlayerQuotes(playerId, { strategy, at: atRequest.date }, { signal: controller.signal })
      .then((found) => {
        if (!controller.signal.aborted) setAtQuery({ ...IDLE_AT, status: 'found', quote: found, date: atRequest.date })
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        // El jugador ya se cargó: un 404 acá significa que no había cotización vigente en esa fecha.
        const status = error instanceof ApiError && error.status === 404 ? 'empty' : 'error'
        setAtQuery({ ...IDLE_AT, status, date: atRequest.date })
      })

    return () => controller.abort()
  }, [playerId, strategy, atRequest])

  const setStrategy = useCallback((nextStrategy) => {
    setHistory(LOADING_HISTORY)
    setAtRequest(null)
    setAtQuery(IDLE_AT)
    setSelection((current) => ({ ...current, strategy: nextStrategy }))
  }, [])

  const setRange = useCallback((nextRange) => {
    setHistory(LOADING_HISTORY)
    setSelection((current) => ({ ...current, range: nextRange }))
  }, [])

  const retry = useCallback(() => {
    setState({ status: 'loading', detail: null })
    setHistory(LOADING_HISTORY)
    setRetryKey((key) => key + 1)
  }, [])

  const retryHistory = useCallback(() => {
    setHistory(LOADING_HISTORY)
    setHistoryRetryKey((key) => key + 1)
  }, [])

  const queryAt = useCallback((date) => {
    if (!date || date > today) {
      setAtRequest(null)
      setAtQuery({ ...IDLE_AT, date, validationError: INVALID_DATE })
      return
    }
    setAtQuery({ ...IDLE_AT, status: 'loading', date })
    setAtRequest({ date })
  }, [today])

  const retryAt = useCallback(() => {
    setAtQuery((current) => ({ ...current, status: 'loading' }))
    setAtRequest((current) => (current ? { date: current.date } : current))
  }, [])

  return {
    status: state.status,
    detail: state.detail,
    variation: { current: quote?.value ?? null, previous: previousValue },
    retry,
    strategy,
    comparisonCodes: comparisonKey ? comparisonKey.split(',') : null,
    range,
    setStrategy,
    setRange,
    history,
    retryHistory,
    today,
    atQuery,
    queryAt,
    retryAt,
  }
}
