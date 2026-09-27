import Button from '../../components/Button.jsx'
import Field from '../../components/Field.jsx'
import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import './AlbumFilters.css'

export default function AlbumFilters({ value, onChange, teamOptions, viewMode, onViewChange, onClear }) {
  return (
    <section className="album-filters" aria-label="Filtros del álbum">
      <div className="album-filters__fields">
        <Field id="album-league" as="select" label="Liga" value={value.league} onChange={(event) => onChange('league', event.target.value)}>
          <option value="">Todas</option>
          {Object.entries(LEAGUES).map(([key, league]) => <option value={key} key={key}>{league.label}</option>)}
        </Field>
        <Field id="album-position" as="select" label="Posición" value={value.position} onChange={(event) => onChange('position', event.target.value)}>
          <option value="">Todas</option>
          {Object.entries(POSITIONS).map(([key, position]) => <option value={key} key={key}>{position.label}</option>)}
        </Field>
        <Field id="album-team" as="select" label="Equipo" value={value.team} onChange={(event) => onChange('team', event.target.value)}>
          <option value="">Todos</option>
          {teamOptions.map((option) => <option value={option.value} key={option.value}>{option.label}</option>)}
        </Field>
        <Field id="album-search" label="Buscar por nombre" value={value.search} onChange={(event) => onChange('search', event.target.value)} />
      </div>
      <div className="album-filters__actions">
        <Button type="button" variant="text" onClick={onClear}>Limpiar filtros</Button>
        <div className="view-toggle" aria-label="Vista del álbum">
          <button type="button" aria-pressed={viewMode === 'cards'} onClick={() => onViewChange('cards')}>▦ Cartas</button>
          <button type="button" aria-pressed={viewMode === 'list'} onClick={() => onViewChange('list')}>☰ Lista</button>
        </div>
      </div>
    </section>
  )
}
