import Button from './Button.jsx'
import Sello from './Sello.jsx'
import './EstadoPanel.css'

export default function EstadoPanel({ stamp, stampVariant = 'neutral', title, children, action }) {
  return (
    <section className="estado-panel">
      <Sello variant={stampVariant}>{stamp}</Sello>
      <h2>{title}</h2>
      <p>{children}</p>
      {action ? <Button variant="secondary" type="button" onClick={action.onClick}>{action.label}</Button> : null}
    </section>
  )
}
