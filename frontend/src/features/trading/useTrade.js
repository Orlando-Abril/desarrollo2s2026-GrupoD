import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { buyOrder, sellOrder } from '../../api/marketApi.js'
import { marketErrorMessage, staleTarget } from './marketErrors.js'
import {
  bandFor,
  estimateLimit,
  estimateMarket,
  fromUnits,
  limitPriceUnits,
  summarizeOrder,
  validateDraft,
} from './tradeMath.js'

const INITIAL_DRAFT = { side: 'BUY', type: 'MARKET', quantity: '', limitPrice: '' }
const NO_TOUCHED = { quantity: false, limitPrice: false }

function buildEstimate(draft, errors, book) {
  if (errors.quantity || !book) return null
  const quantity = Number(draft.quantity)
  if (draft.type === 'LIMIT') {
    if (errors.limitPrice) return null
    return { kind: 'limit', total: estimateLimit(quantity, limitPriceUnits(draft.limitPrice)) }
  }
  const levels = draft.side === 'BUY' ? book.sells : book.buys
  return { kind: 'market', ...estimateMarket(levels, quantity) }
}

function visibleErrors(errors, touched, attempted) {
  const shown = {}
  for (const [field, message] of Object.entries(errors)) {
    if (attempted || touched[field]) shown[field] = message
  }
  return shown
}

/** Borrador, validación, estimado y ciclo editing → confirming → sending de una orden (data-model). */
export default function useTrade({ playerId, book, onExecuted, onStale }) {
  const [draft, setDraft] = useState(INITIAL_DRAFT)
  const [touched, setTouched] = useState(NO_TOUCHED)
  const [attempted, setAttempted] = useState(false)
  const [phase, setPhase] = useState('editing')
  const [submitError, setSubmitError] = useState(null)
  const [lastResult, setLastResult] = useState(null)
  const sendingRef = useRef(null)

  useEffect(() => () => sendingRef.current?.abort(), [])

  const band = useMemo(() => bandFor(book?.marketValue), [book])
  const allErrors = useMemo(() => validateDraft(draft, band), [draft, band])
  const errors = visibleErrors(allErrors, touched, attempted)
  const estimate = useMemo(() => buildEstimate(draft, allErrors, book), [draft, allErrors, book])

  const setField = useCallback((name, value) => {
    setDraft((current) => {
      const next = { ...current, [name]: value }
      if (name === 'type' && value === 'MARKET') next.limitPrice = ''
      return next
    })
    if (name === 'type') setTouched((current) => ({ ...current, limitPrice: false }))
    setSubmitError(null)
    setLastResult(null)
  }, [])

  const touchField = useCallback((name) => {
    setTouched((current) => ({ ...current, [name]: true }))
  }, [])

  const submit = useCallback(() => {
    setAttempted(true)
    if (Object.keys(allErrors).length > 0) return allErrors
    setSubmitError(null)
    setPhase('confirming')
    return {}
  }, [allErrors])

  const cancelConfirm = useCallback(() => setPhase('editing'), [])

  const confirm = useCallback(async () => {
    if (sendingRef.current) return
    const controller = new AbortController()
    sendingRef.current = controller
    setPhase('sending')
    const order = { playerId, quantity: Number(draft.quantity), type: draft.type }
    if (draft.type === 'LIMIT') order.limitPrice = fromUnits(limitPriceUnits(draft.limitPrice))
    const send = draft.side === 'BUY' ? buyOrder : sellOrder

    try {
      const result = await send(order, { signal: controller.signal })
      if (controller.signal.aborted) return
      setLastResult(summarizeOrder(result))
      setDraft((current) => ({ ...current, quantity: '', limitPrice: '' }))
      setTouched(NO_TOUCHED)
      setAttempted(false)
      onExecuted?.(result)
    } catch (error) {
      if (controller.signal.aborted) return
      setSubmitError(marketErrorMessage(error))
      if (staleTarget(error) === 'book') onStale?.()
    } finally {
      if (!controller.signal.aborted) {
        sendingRef.current = null
        setPhase('editing')
      }
    }
  }, [draft, onExecuted, onStale, playerId])

  return { draft, setField, touchField, errors, band, estimate, phase, submitError, lastResult, submit, confirm, cancelConfirm }
}
