import { useEffect, useRef, useState } from 'react'
import { getCurrentAccount } from '../../api/portfolioApi.js'
import { changeActiveStrategy } from '../../api/strategyApi.js'
import Toast from '../../components/Toast.jsx'
import { useStrategyView } from '../../context/StrategyViewContext.jsx'
import './ActiveStrategyControl.css'

export default function ActiveStrategyControl({ onChanged, onFocusRecalculate }) {
  const { activeConfig, configStatus, ensureConfig, refreshConfig } = useStrategyView()
  const [role, setRole] = useState(null)
  const [requested, setRequested] = useState(null)
  const [busy, setBusy] = useState(false)
  const inFlight = useRef(false)
  const [notice, setNotice] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    getCurrentAccount({ signal: controller.signal }).then((account) => {
      if (!controller.signal.aborted) setRole(account.role)
    }).catch(() => { if (!controller.signal.aborted) setRole('UNKNOWN') })
    return () => controller.abort()
  }, [])
  useEffect(() => { if (role === 'ADMIN') void ensureConfig() }, [role, ensureConfig])
  const selected = requested ?? activeConfig?.active?.code ?? ''

  if (role !== 'ADMIN') return notice ? <Toast onDismiss={() => setNotice(null)}>{notice}</Toast> : null

  const change = async () => {
    if (inFlight.current || !activeConfig || selected === activeConfig.active?.code) return
    const previous = activeConfig.active.code
    if (!window.confirm(`Cambiar la estrategia activa de ${previous} a ${selected}? El precio de mercado cambiará con el próximo recálculo.`)) return
    inFlight.current = true
    setBusy(true)
    setNotice(null)
    try {
      const result = await changeActiveStrategy(selected)
      await refreshConfig()
      setRequested(null)
      onChanged?.()
      setNotice(result.changed === false ? 'La estrategia activa ya tenía ese valor. El precio de mercado se actualiza con el próximo recálculo.' : `Estrategia activa: ${selected}. El precio de mercado cambia con el próximo recálculo.`)
    } catch (error) {
      if (error?.status === 403) {
        setRole('USER')
        setNotice('No tenés permisos para cambiar la estrategia activa.')
      } else if (error?.status === 400) {
        await refreshConfig()
        setRequested(null)
        setNotice('La estrategia elegida no es válida. Revisá la estrategia activa y volvé a intentar.')
      } else {
        await refreshConfig()
        setRequested(null)
        setNotice('No pudimos confirmar el cambio por un problema de red. Revisá la estrategia activa antes de reintentar.')
      }
    } finally { inFlight.current = false; setBusy(false) }
  }

  return <div className="active-strategy-control">
    <label htmlFor="active-strategy">Estrategia activa</label>
    <select id="active-strategy" value={selected} disabled={busy || configStatus !== 'ready'} onChange={(event) => setRequested(event.target.value)}>
      {[activeConfig?.active, activeConfig?.alternative].filter(Boolean).map((strategy) => <option key={strategy.code} value={strategy.code}>{strategy.label ?? strategy.code}</option>)}
    </select>
    <button type="button" disabled={busy || configStatus !== 'ready' || selected === activeConfig?.active?.code} onClick={() => { void change() }}>Cambiar estrategia activa</button>
    {configStatus === 'error' ? <button type="button" onClick={() => { void refreshConfig() }}>Reintentar carga</button> : null}
    {activeConfig?.pendingRecalculation ? <small>Mercado actual: {activeConfig.marketStrategy?.code}. Recálculo pendiente.</small> : null}
    {notice ? <Toast onDismiss={() => setNotice(null)}><span>{notice}</span>{notice.includes('próximo recálculo') && onFocusRecalculate ? <button type="button" onClick={onFocusRecalculate}>Ir a Recalcular cotizaciones</button> : null}</Toast> : null}
  </div>
}
