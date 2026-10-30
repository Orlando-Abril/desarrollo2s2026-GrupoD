import { useCallback, useEffect, useState } from 'react'
import { getOrderBook } from '../../api/marketApi.js'

export default function useOrderBook(playerId) {
  const [book, setBook] = useState(null)
  const [status, setStatus] = useState('loading')
  const [retryKey, setRetryKey] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

    getOrderBook(playerId, { signal: controller.signal })
      .then((response) => {
        if (controller.signal.aborted) return
        setBook(response)
        setStatus('ready')
      })
      .catch(() => {
        if (controller.signal.aborted) return
        setStatus('error')
      })

    return () => controller.abort()
  }, [playerId, retryKey])

  // Recargar con el libro ya visible no vuelve a 'loading': el panel sigue usable mientras llega el nuevo.
  const reload = useCallback(() => {
    setStatus((current) => (current === 'error' ? 'loading' : current))
    setRetryKey((key) => key + 1)
  }, [])

  return { status, book, reload }
}
