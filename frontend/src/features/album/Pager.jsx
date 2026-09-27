import { formatInteger } from '../../utils/format.js'
import './Pager.css'

export default function Pager({ page, totalPages, from, to, total, window, onPageChange }) {
  if (total === 0) return null
  return (
    <div className="pager">
      <p className="pager__range" aria-live="polite">
        Mostrando {formatInteger(from)}–{formatInteger(to)} de {formatInteger(total)}
      </p>
      <nav className="pager__controls" aria-label="Paginación">
        <button type="button" aria-label="Página anterior" disabled={page === 1} onClick={() => onPageChange(page - 1)}>‹</button>
        {window.map((item, index) => item === 'ellipsis'
          ? <span className="pager__ellipsis" aria-hidden="true" key={`ellipsis-${index}`}>…</span>
          : (
            <button
              type="button"
              aria-label={`Página ${item}`}
              aria-current={item === page ? 'page' : undefined}
              onClick={() => onPageChange(item)}
              key={item}
            >
              {formatInteger(item)}
            </button>
          ))}
        <button type="button" aria-label="Página siguiente" disabled={page === totalPages} onClick={() => onPageChange(page + 1)}>›</button>
      </nav>
    </div>
  )
}
