import { forwardRef } from 'react'
import './Field.css'

const Field = forwardRef(function Field({ id, label, hint, error, as = 'input', children, ...controlProps }, ref) {
  const descriptionId = error || hint ? `${id}-description` : undefined
  const Control = as
  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      <label htmlFor={id}>{label}</label>
      <Control
        ref={ref}
        id={id}
        aria-invalid={error ? 'true' : undefined}
        aria-describedby={descriptionId}
        {...controlProps}
      >
        {children}
      </Control>
      {error ? <p id={descriptionId} className="field__error">{error}</p> : null}
      {!error && hint ? <p id={descriptionId} className="field__hint">{hint}</p> : null}
    </div>
  )
})

export default Field
