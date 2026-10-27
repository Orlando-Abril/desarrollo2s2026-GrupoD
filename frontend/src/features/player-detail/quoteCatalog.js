export const STRATEGIES = {
  OFFICIAL: { label: 'Oficial' },
  MOMENTUM: { label: 'Momentum' },
}

export const QUOTE_RANGES = {
  month: { label: 'Último mes', days: 30 },
  quarter: { label: '3 meses', days: 90 },
  season: { label: 'Toda la temporada', days: null },
}

export const STAT_FIELDS = [
  { key: 'minutesPlayed', label: 'Minutos' },
  { key: 'goals', label: 'Goles' },
  { key: 'assists', label: 'Asistencias' },
  { key: 'shots', label: 'Tiros' },
  { key: 'keyPasses', label: 'Pases clave' },
  { key: 'tackles', label: 'Tackles' },
  { key: 'yellowCards', label: 'Amarillas' },
  { key: 'redCards', label: 'Rojas' },
  { key: 'rating', label: 'Rating' },
]
