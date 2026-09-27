export const PAGE_SIZE = 24

export function normalizeText(value = '') {
  return String(value)
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLocaleLowerCase('es')
    .trim()
}

export function filterByName(players, search) {
  const query = normalizeText(search)
  return query ? players.filter((player) => normalizeText(player.fullName).includes(query)) : players
}

export function sortPlayers(players) {
  return [...players].sort((left, right) => left.fullName.localeCompare(right.fullName, 'es'))
}

export function deriveTeamOptions(players) {
  return [...new Set(players.map(({ team }) => team))]
    .sort((left, right) => left.localeCompare(right, 'es'))
    .map((team) => ({ value: team, label: team }))
}

export function paginate(players, requestedPage, pageSize = PAGE_SIZE) {
  const total = players.length
  if (total === 0) {
    return { total: 0, totalPages: 0, page: 1, from: 0, to: 0, items: [] }
  }
  const totalPages = Math.ceil(total / pageSize)
  const page = Math.min(Math.max(Number(requestedPage) || 1, 1), totalPages)
  const start = (page - 1) * pageSize
  const items = players.slice(start, start + pageSize)
  return { total, totalPages, page, from: start + 1, to: start + items.length, items }
}

export function buildPageWindow(page, totalPages) {
  if (totalPages === 0) return []
  const pages = [...new Set([1, page - 1, page, page + 1, totalPages]
    .filter((candidate) => candidate >= 1 && candidate <= totalPages))]
    .sort((left, right) => left - right)
  return pages.flatMap((candidate, index) => {
    if (index === 0 || candidate - pages[index - 1] === 1) return [candidate]
    return ['ellipsis', candidate]
  })
}
