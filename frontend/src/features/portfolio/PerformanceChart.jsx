import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis, ResponsiveContainer } from 'recharts'
import { formatCredits } from './portfolioFormat.js'
import { toPerformanceData } from './portfolioViewModel.js'
import { prefersReducedMotion } from './portfolioMotion.js'
import './PerformanceChart.css'

export default function PerformanceChart({ positions }) {
  const data = toPerformanceData(positions)
  const caption = `Precio promedio y cotización actual por jugador · ${data.length} posiciones`
  return <figure className="portfolio-chart">
    <h2>Rendimiento del portfolio</h2><figcaption>{caption}</figcaption>
    <div role="img" aria-label={caption} className="portfolio-chart__canvas"><ResponsiveContainer width="100%" height={300}><BarChart data={data}><CartesianGrid strokeDasharray="3 3"/><XAxis dataKey="playerName"/><YAxis/><Tooltip formatter={(value) => formatCredits(value)}/><Bar dataKey="averagePurchasePrice" name="Precio promedio" fill="#1f4fd1" isAnimationActive={!prefersReducedMotion()}/><Bar dataKey="marketValue" name="Cotización actual" fill="#12805c" isAnimationActive={!prefersReducedMotion()}/></BarChart></ResponsiveContainer></div>
    <ul className="portfolio-chart__legend" aria-label="Leyenda"><li><i className="swatch swatch--average"/>Precio promedio</li><li><i className="swatch swatch--market"/>Cotización actual</li></ul>
    <details><summary>Ver datos en tabla</summary><table><thead><tr><th scope="col">Jugador</th><th scope="col">Precio promedio</th><th scope="col">Cotización actual</th></tr></thead><tbody>{data.map((row) => <tr key={row.playerId}><th scope="row">{row.playerName}</th><td>{formatCredits(row.averagePurchasePrice)}</td><td>{formatCredits(row.marketValue)}</td></tr>)}</tbody></table></details>
  </figure>
}
