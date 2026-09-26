import './Banner.css'

export default function Banner({ variant = 'error', children }) {
  return (
    <div className={`banner banner--${variant}`} role={variant === 'error' ? 'alert' : undefined}>
      {variant === 'error' ? `✕ ${children}` : children}
    </div>
  )
}
