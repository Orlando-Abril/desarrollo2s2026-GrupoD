import './Carnet.css'

const variants = {
  login: { title: 'Ingresar', badge: 'Socio' },
  register: { title: 'Carnet de coleccionista', badge: 'Nuevo' },
}

export default function Carnet({ variant, children, footer }) {
  const content = variants[variant]
  return (
    <section className={`carnet carnet--${variant}`}>
      <header className="carnet__header">
        <h1>{content.title}</h1>
        <span>{content.badge}</span>
      </header>
      <div className="carnet__body">{children}</div>
      <footer className="carnet__footer">{footer}</footer>
    </section>
  )
}
