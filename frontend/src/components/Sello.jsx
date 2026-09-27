import './Sello.css'

export default function Sello({ variant = 'neutral', children }) {
  return <span className={`sello sello--${variant}`}>{children}</span>
}
