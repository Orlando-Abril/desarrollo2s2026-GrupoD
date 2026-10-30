import { useCallback, useEffect, useRef, useState } from 'react'
import { recalculateQuotes } from '../../api/quotesApi.js'
import { ApiError, NetworkError } from '../../api/httpClient.js'
import { useSession } from '../../session/SessionContext.jsx'

let lastDeniedToken = null

export default function useRecalculate({ onRecalculated }) {
  const { token } = useSession()
  const [runningToken, setRunningToken] = useState(null)
  const [notificationState, setNotificationState] = useState(null)
  const inFlightRef = useRef(false)
  const controllerRef = useRef(null)
  const mountedRef = useRef(true)
  const tokenRef = useRef(token)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
      controllerRef.current?.abort()
    }
  }, [])

  useEffect(() => {
    if (tokenRef.current !== token) controllerRef.current?.abort()
    tokenRef.current = token
  }, [token])

  const forbidden = Boolean(token && token === lastDeniedToken)
  const running = runningToken !== null && runningToken === token
  const notification = notificationState?.token === token ? notificationState.value : null
  const publish = (value, ownerToken) => setNotificationState({ token: ownerToken, value })
  const dismissNotification = useCallback(() => setNotificationState(null), [])

  const recalculate = useCallback(async () => {
    if (inFlightRef.current || forbidden) return
    inFlightRef.current = true
    const controller = new AbortController()
    controllerRef.current = controller
    const requestToken = token
    setRunningToken(requestToken)
    setNotificationState(null)

    try {
      const summary = await recalculateQuotes({}, { signal: controller.signal })
      if (!mountedRef.current || controller.signal.aborted || tokenRef.current !== requestToken) return
      publish({ type: 'success', summary }, requestToken)
      onRecalculated?.()
    } catch (error) {
      if (!mountedRef.current || controller.signal.aborted || tokenRef.current !== requestToken) return
      if (error instanceof ApiError && error.status === 403) {
        lastDeniedToken = requestToken
        publish({ type: 'forbidden' }, requestToken)
      } else if (error instanceof ApiError && error.status === 409) {
        publish({ type: 'in_progress' }, requestToken)
      } else if (error instanceof NetworkError) {
        publish({ type: 'network_error' }, requestToken)
      } else {
        publish({ type: 'unexpected_error' }, requestToken)
      }
    } finally {
      if (controllerRef.current === controller) controllerRef.current = null
      inFlightRef.current = false
      if (mountedRef.current && tokenRef.current === requestToken) setRunningToken(null)
    }
  }, [forbidden, onRecalculated, token])

  return { running, notification, forbidden, recalculate, dismissNotification }
}
