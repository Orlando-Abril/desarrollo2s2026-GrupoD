import { useCallback, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import Toast from '../components/Toast.jsx'
import Casillero from '../components/Casillero.jsx'
import EstadoPanel from '../components/EstadoPanel.jsx'
import AlbumFilters from '../features/album/AlbumFilters.jsx'
import AlbumGrid from '../features/album/AlbumGrid.jsx'
import AlbumTable from '../features/album/AlbumTable.jsx'
import Pager from '../features/album/Pager.jsx'
import useAlbum from '../features/album/useAlbum.js'
import { formatInteger } from '../utils/format.js'
import './AlbumPage.css'

export default function AlbumPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const [toast, setToast] = useState(location.state?.toast ?? null)
  const resultsRef = useRef(null)
  const album = useAlbum()
  const dismissToast = useCallback(() => {
    setToast(null)
    navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, navigate])

  const changePage = useCallback((nextPage) => {
    if (nextPage === album.page || nextPage < 1 || nextPage > album.totalPages) return
    album.setPage(nextPage)
    resultsRef.current?.scrollIntoView({ block: 'start' })
  }, [album])

  let content
  if (album.status === 'loading') {
    content = (
      <div className="album-loading" aria-busy="true" aria-label="Cargando figuritas…">
        {Array.from({ length: 8 }, (_, index) => <Casillero variant="cargando" key={index}>Cargando figuritas…</Casillero>)}
      </div>
    )
  } else if (album.status === 'error') {
    content = (
      <EstadoPanel
        stamp="Sin conexión"
        stampVariant="error"
        title="No pudimos abrir el álbum"
        action={{ label: 'Reintentar', onClick: album.retry }}
      >
        No se pudo conectar con el servidor. Intentá de nuevo en unos segundos.
      </EstadoPanel>
    )
  } else if (!album.hasActiveFilters && album.responsePlayers.length === 0) {
    content = (
      <EstadoPanel stamp="Álbum vacío" title="Todavía no hay figuritas">
        El catálogo de jugadores aún no fue cargado. Probá de nuevo más tarde.
      </EstadoPanel>
    )
  } else if (album.total === 0) {
    content = (
      <EstadoPanel
        stamp="0 resultados"
        title="No hay figuritas con esos filtros"
        action={{ label: 'Limpiar filtros', onClick: album.clearFilters }}
      >
        Probá con otra liga, equipo o posición.
      </EstadoPanel>
    )
  } else {
    content = album.viewMode === 'cards'
      ? <AlbumGrid players={album.items} />
      : <AlbumTable players={album.items} />
  }

  return (
    <div className="page-content album-page">
      <header className="page-heading">
        <h1>El álbum</h1>
        {album.initialTotal !== null ? <p>{formatInteger(album.initialTotal)} jugadores · 5 ligas</p> : null}
      </header>
      <AlbumFilters
        value={album.filters}
        onChange={album.changeFilter}
        teamOptions={album.teamOptions}
        viewMode={album.viewMode}
        onViewChange={album.setViewMode}
        onClear={album.clearFilters}
      />
      <div className="album-results" ref={resultsRef}>{content}</div>
      {album.status === 'ready' && album.total > 0 ? (
        <Pager
          page={album.page}
          totalPages={album.totalPages}
          from={album.from}
          to={album.to}
          total={album.total}
          window={album.pageWindow}
          onPageChange={changePage}
        />
      ) : null}
      {toast ? <Toast onDismiss={dismissToast}>{toast}</Toast> : null}
    </div>
  )
}
