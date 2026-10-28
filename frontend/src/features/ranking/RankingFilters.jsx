import Field from '../../components/Field.jsx'
import { LEAGUES, POSITIONS } from '../../domain/catalogo.js'
import './RankingFilters.css'

export default function RankingFilters({ value, onChange, effectiveStrategy }) {
  const activeLabel = value.strategy === '' && effectiveStrategy
    ? `Activa (${effectiveStrategy})`
    : 'Activa'

  return (
    <section className="ranking-filters" aria-label="Filtros del ranking">
      <Field
        id="ranking-strategy"
        as="select"
        label="Estrategia"
        value={value.strategy}
        onChange={(event) => onChange('strategy', event.target.value)}
      >
        <option value="">{activeLabel}</option>
        <option value="OFFICIAL">OFFICIAL</option>
        <option value="MOMENTUM">MOMENTUM</option>
      </Field>
      <Field
        id="ranking-league"
        as="select"
        label="Liga"
        value={value.league}
        onChange={(event) => onChange('league', event.target.value)}
      >
        <option value="">Todas</option>
        {Object.entries(LEAGUES).map(([key, league]) => (
          <option value={key} key={key}>{league.label}</option>
        ))}
      </Field>
      <Field
        id="ranking-position"
        as="select"
        label="Posición"
        value={value.position}
        onChange={(event) => onChange('position', event.target.value)}
      >
        <option value="">Todas</option>
        {Object.entries(POSITIONS).map(([key, position]) => (
          <option value={key} key={key}>{position.label}</option>
        ))}
      </Field>
    </section>
  )
}
