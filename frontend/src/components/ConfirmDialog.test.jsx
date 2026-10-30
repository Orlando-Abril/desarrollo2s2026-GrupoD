import { useState } from 'react'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import ConfirmDialog from './ConfirmDialog.jsx'

function Harness({ onConfirm = vi.fn(), onCancel, busy = false }) {
  const [open, setOpen] = useState(false)
  const close = () => {
    onCancel?.()
    setOpen(false)
  }
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>Abrir</button>
      <ConfirmDialog
        open={open}
        title="Confirmar compra"
        confirmLabel="Confirmar"
        onConfirm={onConfirm}
        onCancel={close}
        busy={busy}
      >
        Resumen de la orden
      </ConfirmDialog>
    </>
  )
}

function openDialog(props) {
  render(<Harness {...props} />)
  const opener = screen.getByRole('button', { name: 'Abrir' })
  opener.focus()
  fireEvent.click(opener)
  return { opener, dialog: screen.getByRole('dialog') }
}

describe('ConfirmDialog', () => {
  afterEach(cleanup)

  it('renders nothing while closed', () => {
    render(<Harness />)
    expect(screen.queryByRole('dialog')).toBeNull()
  })

  it('is a labelled modal dialog with the focus on confirm', () => {
    const { dialog } = openDialog()

    expect(dialog.getAttribute('aria-modal')).toBe('true')
    const title = document.getElementById(dialog.getAttribute('aria-labelledby'))
    expect(title.textContent).toBe('Confirmar compra')
    expect(document.activeElement).toBe(screen.getByRole('button', { name: 'Confirmar' }))
  })

  it('traps Tab and Shift+Tab inside the dialog', () => {
    const { dialog } = openDialog()
    const confirm = screen.getByRole('button', { name: 'Confirmar' })
    const back = screen.getByRole('button', { name: 'Volver' })

    confirm.focus()
    fireEvent.keyDown(dialog, { key: 'Tab' })
    expect(document.activeElement).toBe(back)

    back.focus()
    fireEvent.keyDown(dialog, { key: 'Tab', shiftKey: true })
    expect(document.activeElement).toBe(confirm)
  })

  it('calls onCancel with Escape and with Volver, and onConfirm with confirm', () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    const { dialog } = openDialog({ onConfirm, onCancel })

    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(onCancel).toHaveBeenCalledTimes(1)

    fireEvent.click(screen.getByRole('button', { name: 'Abrir' }))
    fireEvent.click(screen.getByRole('button', { name: 'Volver' }))
    expect(onCancel).toHaveBeenCalledTimes(2)

    fireEvent.click(screen.getByRole('button', { name: 'Abrir' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('disables both buttons and ignores Escape while busy', () => {
    const onCancel = vi.fn()
    const { dialog } = openDialog({ onCancel, busy: true })

    expect(screen.getByRole('button', { name: 'Confirmar' }).disabled).toBe(true)
    expect(screen.getByRole('button', { name: 'Volver' }).disabled).toBe(true)
    fireEvent.keyDown(dialog, { key: 'Escape' })
    expect(onCancel).not.toHaveBeenCalled()
  })

  it('returns the focus to the opener when it closes', () => {
    const { opener } = openDialog()

    fireEvent.click(screen.getByRole('button', { name: 'Volver' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(document.activeElement).toBe(opener)
  })
})
