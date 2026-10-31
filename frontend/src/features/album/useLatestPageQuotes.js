import { useCallback, useEffect, useMemo, useState } from 'react'
import { getLatestQuotes } from '../../api/comparisonApi.js'

export default function useLatestPageQuotes(players, strategy) {
  const idsKey = useMemo(() => [...new Set(players.map((player) => player.id))].slice(0, 100).join(','), [players])
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState({ status: 'idle', quotes: {}, error: null, key: '' })
  const retry = useCallback(() => setAttempt((value) => value + 1), [])
  const key = `${idsKey}|${strategy}|${attempt}`
  useEffect(() => {
    if (!strategy || !idsKey) return
    const controller = new AbortController()
    let valid = true
    getLatestQuotes(idsKey.split(',').map(Number), strategy, { signal: controller.signal })
      .then((response) => {
        if (!valid) return
        const quotes = Object.fromEntries((response.items ?? []).map((item) => [item.playerId, item.quote]))
        setState({ status: 'ready', quotes, error: null, key })
      })
      .catch((error) => { if (valid) setState({ status: 'error', quotes: {}, error, key }) })
    return () => { valid = false; controller.abort() }
  }, [idsKey, strategy, attempt, key])
  if (!strategy || !idsKey) return { status: 'idle', quotes: {}, error: null, retry }
  if (state.key !== key) return { status: 'loading', quotes: {}, error: null, retry }
  return { ...state, retry }
}
