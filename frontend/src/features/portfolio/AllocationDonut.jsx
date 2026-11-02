import { Pie, PieChart, ResponsiveContainer, Sector, Tooltip } from 'recharts'
import { formatCredits } from './portfolioFormat.js'
import { toAllocationData } from './portfolioViewModel.js'
import { prefersReducedMotion } from './portfolioMotion.js'
import './PerformanceChart.css'
import './AllocationDonut.css'

function AllocationSector({ payload, ...sectorProps }) {
  return <Sector {...sectorProps} fill={payload.color}/>
}

export default function AllocationDonut({ positions, totalCurrentValue }) {
  const data = toAllocationData(positions, totalCurrentValue)
  const valid = Number.isFinite(Number(totalCurrentValue)) && Number(totalCurrentValue) > 0
  const total = positions?.length ?? 0
  const grouped = data.length < total ? ', las ' + (data.length - 1) + ' de mayor valor y el resto en Otros' : ''
  const caption = `Distribución por valor actual · ${total} posiciones${grouped}`
  return <figure className="portfolio-chart"><h2>Distribución de activos</h2><figcaption>{caption}</figcaption>
    {valid ? <div role="img" aria-label={caption} className="portfolio-chart__canvas"><ResponsiveContainer width="100%" height={300}><PieChart><Pie data={data} dataKey="currentValue" nameKey="playerName" innerRadius={65} outerRadius={105} isAnimationActive={!prefersReducedMotion()} shape={AllocationSector}/><Tooltip formatter={(value) => formatCredits(value)}/></PieChart></ResponsiveContainer></div> : <p>No se puede calcular la distribución porque el valor actual total es cero.</p>}
    {valid ? <ul className="portfolio-chart__legend" aria-label="Leyenda">{data.map((row) => <li key={row.playerId}><i className="swatch" style={{ background: row.color }}/>{row.playerName}: {row.percentage.toFixed(1).replace('.', ',')}%</li>)}</ul> : null}
    <details><summary>Ver datos en tabla</summary><table><thead><tr><th scope="col">Jugador</th><th scope="col">Valor actual</th><th scope="col">Participación</th></tr></thead><tbody>{data.map((row) => <tr key={row.playerId}><th scope="row">{row.playerName}</th><td>{formatCredits(row.currentValue)}</td><td>{row.percentage === null ? '—' : `${row.percentage.toFixed(1).replace('.', ',')}%`}</td></tr>)}</tbody></table></details>
  </figure>
}
