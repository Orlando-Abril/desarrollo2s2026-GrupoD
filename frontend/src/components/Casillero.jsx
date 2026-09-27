import './Casillero.css'

export default function Casillero({ variant = 'vacío', mark = '···', children }) {
  return (
    <div className={`casillero casillero--${variant}`}>
      <strong>{mark}</strong>
      <span>{children}</span>
    </div>
  )
}
