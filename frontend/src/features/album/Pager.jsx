import { Fragment } from 'react'
import { formatInteger } from '../../utils/format.js'
import './Pager.css'

export default function Pager({ page, totalPages, from, to, total, window, onPageChange }) {
  if (total === 0) return null
  // Cada elipsis se renderiza junto a la página que la sigue, así ambas comparten una key estable.
  const pages = window.filter((item) => item !== 'ellipsis')
  return (
    <div className="pager">
      <p className="pager__range" aria-live="polite">
        Mostrando {formatInteger(from)}–{formatInteger(to)} de {formatInteger(total)}
      </p>
      <nav className="pager__controls" aria-label="Paginación">
        <button type="button" aria-label="Página anterior" disabled={page === 1} onClick={() => onPageChange(page - 1)}>‹</button>
        {pages.map((item) => (
          <Fragment key={item}>
            {window[window.indexOf(item) - 1] === 'ellipsis'
              ? <span className="pager__ellipsis" aria-hidden="true">…</span>
              : null}
            <button
              type="button"
              aria-label={`Página ${item}`}
              aria-current={item === page ? 'page' : undefined}
              onClick={() => onPageChange(item)}
            >
              {formatInteger(item)}
            </button>
          </Fragment>
        ))}
        <button type="button" aria-label="Página siguiente" disabled={page === totalPages} onClick={() => onPageChange(page + 1)}>›</button>
      </nav>
    </div>
  )
}
