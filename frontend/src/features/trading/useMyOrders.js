import { useCallback, useEffect, useRef, useState } from 'react'
import { cancelOrder, getMyOrders } from '../../api/marketApi.js'
import { marketErrorMessage, staleTarget } from './marketErrors.js'
import { summarizeCancel } from './tradeMath.js'

export default function useMyOrders() {
  const [orders, setOrders] = useState([])
  const [status, setStatus] = useState('loading')
  const [loadError, setLoadError] = useState(null)
  const [retryKey, setRetryKey] = useState(0)
  const [cancellingId, setCancellingId] = useState(null)
  const [cancelError, setCancelError] = useState(null)
  const mountedRef = useRef(true)

  useEffect(() => () => {
    mountedRef.current = false
  }, [])

  useEffect(() => {
    const controller = new AbortController()

    getMyOrders({ signal: controller.signal })
      .then((response) => {
        if (controller.signal.aborted) return
        setOrders(response)
        setLoadError(null)
        setStatus('ready')
      })
      .catch((error) => {
        if (controller.signal.aborted) return
        setLoadError(error)
        setStatus('error')
      })

    return () => controller.abort()
  }, [retryKey])

  const reload = useCallback(() => {
    setStatus((current) => (current === 'error' ? 'loading' : current))
    setRetryKey((key) => key + 1)
  }, [])

  /** Cancela la orden y devuelve el resumen de lo liberado, o null si falló. */
  const cancel = useCallback(async (order) => {
    setCancellingId(order.id)
    setCancelError(null)
    try {
      await cancelOrder(order.id)
      if (!mountedRef.current) return null
      setOrders((current) => current.filter((item) => item.id !== order.id))
      return summarizeCancel(order)
    } catch (error) {
      if (!mountedRef.current) return null
      setCancelError(marketErrorMessage(error, { action: 'cancel' }))
      if (staleTarget(error) === 'orders') reload()
      return null
    } finally {
      if (mountedRef.current) setCancellingId(null)
    }
  }, [reload])

  return { status, orders, loadError, cancellingId, cancelError, reload, cancel }
}
