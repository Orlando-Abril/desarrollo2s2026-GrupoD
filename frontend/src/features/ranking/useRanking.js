import { useCallback, useEffect, useMemo, useState } from 'react'
import { getRanking } from '../../api/quotesApi.js'
import { buildPageWindow } from '../album/albumUtils.js'

const PAGE_SIZE = 20
const DEFAULT_FILTERS = { strategy: '', league: '', position: '' }
const EMPTY_PAGE = { items: [], page: 0, size: PAGE_SIZE, totalElements: 0, totalPages: 0 }

export default function useRanking() {
  const [filters, setFilters] = useState(DEFAULT_FILTERS)
  const [currentPage, setCurrentPage] = useState(1)
  const [data, setData] = useState(EMPTY_PAGE)
  const [status, setStatus] = useState('loading')
  const [error, setError] = useState(null)
  const [retryKey, setRetryKey] = useState(0)
  const { strategy, league, position } = filters

  useEffect(() => {
    const controller = new AbortController()

    getRanking(
      { strategy, league, position, page: currentPage - 1, size: PAGE_SIZE },
      { signal: controller.signal },
    )
      .then((response) => {
        if (controller.signal.aborted) return
        if (
          response.totalElements > 0
          && response.items.length === 0
          && response.totalPages > 0
          && currentPage > response.totalPages
        ) {
          setStatus('loading')
          setCurrentPage(response.totalPages)
          return
        }
        setData(response)
        setError(null)
        setStatus('ready')
      })
      .catch((requestError) => {
        if (controller.signal.aborted) return
        setError(requestError)
        setStatus('error')
      })

    return () => controller.abort()
  }, [currentPage, league, position, retryKey, strategy])

  const from = data.totalElements === 0 ? 0 : data.page * data.size + 1
  const to = data.page * data.size + data.items.length
  const pageWindow = useMemo(
    () => buildPageWindow(currentPage, data.totalPages),
    [currentPage, data.totalPages],
  )
  const changeFilter = useCallback((name, value) => {
    if (filters[name] === value) return
    setStatus('loading')
    setError(null)
    setFilters((current) => ({ ...current, [name]: value }))
    setCurrentPage(1)
  }, [filters])
  const setPage = useCallback((nextPage) => {
    if (nextPage === currentPage) return
    setStatus('loading')
    setError(null)
    setCurrentPage(nextPage)
  }, [currentPage])
  const retry = useCallback(() => {
    setStatus('loading')
    setError(null)
    setRetryKey((key) => key + 1)
  }, [])
  const reload = retry

  return {
    filters,
    changeFilter,
    status,
    error,
    retry,
    reload,
    page: currentPage,
    setPage,
    items: data.items,
    total: data.totalElements,
    totalPages: data.totalPages,
    from,
    to,
    pageWindow,
    effectiveStrategy: status === 'ready' ? (data.items[0]?.strategy ?? null) : null,
  }
}
