import { useCallback, useEffect, useMemo, useState } from 'react'
import { getPlayers } from '../../api/playersApi.js'
import {
  buildPageWindow,
  deriveTeamOptions,
  filterByName,
  normalizeText,
  paginate,
  sortPlayers,
} from './albumUtils.js'

const DEFAULT_FILTERS = { league: '', position: '', team: '', search: '' }

// Liga y posición también limpian el equipo, así que consultan si cambian o si había un equipo elegido.
function startsRequest(filters, name, value) {
  if (name === 'league' || name === 'position') return value !== filters[name] || Boolean(filters.team)
  if (name === 'team') return value !== filters.team
  return false
}

export default function useAlbum() {
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [responsePlayers, setResponsePlayers] = useState([])
  const [teamSourcePlayers, setTeamSourcePlayers] = useState([])
  const [initialTotal, setInitialTotal] = useState(null)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState(null)
  const [retryKey, setRetryKey] = useState(0)
  const [page, setPage] = useState(1)
  const [viewMode, setViewMode] = useState('cards')

  const { league, position, team } = filters

  useEffect(() => {
    const controller = new AbortController()

    getPlayers({ league, position, team }, { signal: controller.signal })
      .then((players) => {
        if (controller.signal.aborted) return
        setResponsePlayers(players)
        if (!team) setTeamSourcePlayers(players)
        if (!league && !position && !team) {
          setInitialTotal((current) => current ?? players.length)
        }
        setStatus('ready')
      })
      .catch((requestError) => {
        if (controller.signal.aborted) return
        setError(requestError)
        setStatus('error')
      })

    return () => controller.abort()
  }, [league, position, retryKey, team])

  const searchedPlayers = useMemo(
    () => filterByName(responsePlayers, filters.search),
    [filters.search, responsePlayers],
  )
  const sortedPlayers = useMemo(() => sortPlayers(searchedPlayers), [searchedPlayers])
  const pagination = useMemo(() => paginate(sortedPlayers, page), [page, sortedPlayers])
  const pageWindow = useMemo(
    () => buildPageWindow(pagination.page, pagination.totalPages),
    [pagination.page, pagination.totalPages],
  )
  const teamOptions = useMemo(() => deriveTeamOptions(teamSourcePlayers), [teamSourcePlayers])
  const hasActiveFilters = Boolean(league || position || team || normalizeText(filters.search))

  const changeFilter = useCallback((name, value) => {
    const remoteFilters = { league: filters.league, position: filters.position, team: filters.team }
    if (startsRequest(remoteFilters, name, value)) {
      setStatus('loading')
      setError(null)
    }
    setFilters((current) => {
      if (name === 'league' || name === 'position') {
        return { ...current, [name]: value, team: '' }
      }
      return { ...current, [name]: value }
    })
    setPage(1)
  }, [filters.league, filters.position, filters.team])

  const clearFilters = useCallback(() => {
    if (filters.league || filters.position || filters.team) {
      setStatus('loading')
      setError(null)
    }
    setFilters(DEFAULT_FILTERS)
    setPage(1)
  }, [filters.league, filters.position, filters.team])

  const retry = useCallback(() => {
    setStatus('loading')
    setError(null)
    setRetryKey((key) => key + 1)
  }, [])

  return {
    filters,
    changeFilter,
    clearFilters,
    teamOptions,
    initialTotal,
    status,
    error,
    retry,
    page: pagination.page,
    setPage,
    viewMode,
    setViewMode,
    hasActiveFilters,
    responsePlayers,
    ...pagination,
    pageWindow,
  }
}
