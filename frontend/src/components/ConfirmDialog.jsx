import { useEffect, useId, useRef } from 'react'
import Button from './Button.jsx'
import './ConfirmDialog.css'

function trapTab(event, panel) {
  const focusable = [...panel.querySelectorAll('button:not(:disabled)')]
  if (focusable.length === 0) return
  const first = focusable[0]
  const last = focusable.at(-1)
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

export default function ConfirmDialog({
  open,
  title,
  children,
  confirmLabel,
  cancelLabel = 'Volver',
  onConfirm,
  onCancel,
  busy = false,
}) {
  const titleId = useId()
  const bodyId = useId()
  const panelRef = useRef(null)
  const confirmRef = useRef(null)

  // Enfoca confirmar al abrir y devuelve el foco a quien lo abrió al cerrar (research R4).
  useEffect(() => {
    if (!open) return undefined
    const previous = document.activeElement
    confirmRef.current?.focus()
    return () => previous?.focus?.()
  }, [open])

  if (!open) return null

  const handleKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      if (!busy) onCancel()
    } else if (event.key === 'Tab') {
      trapTab(event, panelRef.current)
    }
  }

  return (
    <div className="confirm-dialog">
      <div
        ref={panelRef}
        className="confirm-dialog__panel"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={bodyId}
        onKeyDown={handleKeyDown}
      >
        <h2 id={titleId} className="confirm-dialog__title">{title}</h2>
        <div id={bodyId} className="confirm-dialog__body">{children}</div>
        <div className="confirm-dialog__actions">
          <Button variant="secondary" type="button" onClick={onCancel} disabled={busy}>{cancelLabel}</Button>
          <Button ref={confirmRef} type="button" onClick={onConfirm} disabled={busy}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  )
}
