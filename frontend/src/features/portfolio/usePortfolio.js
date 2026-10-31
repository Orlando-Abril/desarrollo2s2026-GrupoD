import { useCallback, useEffect, useState } from 'react'
import { getCurrentAccount, getUserPortfolio, getUserTransactions } from '../../api/portfolioApi.js'

const EMPTY_PAGE = { items: [], page: 0, size: 20, numberOfElements: 0, totalElements: 0, totalPages: 0, first: true, last: true }

function publishError(error, signal, setter) {
  if (signal.aborted || error?.status === 401) return
  setter('error')
}

export default function usePortfolio() {
  const [identity, setIdentity] = useState({ status: 'loading', account: null, retry: 0 })
  const [portfolioState, setPortfolioState] = useState({ status: 'idle', data: null, refreshing: false, retry: 0 })
  const [transactionsState, setTransactionsState] = useState({ status: 'idle', data: EMPTY_PAGE, refreshing: false, retry: 0 })
  const [page, setPageState] = useState(1)

  useEffect(() => {
    const controller = new AbortController()
    getCurrentAccount({ signal: controller.signal }).then((account) => {
      if (!controller.signal.aborted) setIdentity((current) => ({ ...current, status: 'ready', account }))
    }).catch((error) => publishError(error, controller.signal, (status) => setIdentity((current) => ({ ...current, status }))))
    return () => controller.abort()
  }, [identity.retry])

  useEffect(() => {
    if (!identity.account?.id) return undefined
    const controller = new AbortController()
    getUserPortfolio(identity.account.id, { signal: controller.signal }).then((data) => {
      if (!controller.signal.aborted) setPortfolioState((current) => ({ ...current, data, status: data.positions?.length ? 'ready' : 'empty', refreshing: false }))
    }).catch((error) => {
      if (!controller.signal.aborted && error?.status !== 401) setPortfolioState((current) => ({ ...current, status: 'error', refreshing: false }))
    })
    return () => controller.abort()
  }, [identity.account?.id, portfolioState.retry])

  useEffect(() => {
    if (!identity.account?.id) return undefined
    const controller = new AbortController()
    getUserTransactions(identity.account.id, { page: page - 1, size: 20 }, { signal: controller.signal }).then((data) => {
      if (controller.signal.aborted) return
      const corrected = data.totalPages > 0 ? data.totalPages : 1
      if (page > corrected) {
        setPageState(corrected)
        return
      }
      setTransactionsState((current) => ({ ...current, data, status: data.items?.length ? 'ready' : 'empty', refreshing: false }))
    }).catch((error) => {
      if (!controller.signal.aborted && error?.status !== 401) setTransactionsState((current) => ({ ...current, status: 'error', refreshing: false }))
    })
    return () => controller.abort()
  }, [identity.account?.id, page, transactionsState.retry])

  const totalPages = transactionsState.data?.totalPages ?? 0
  const setPage = useCallback((next) => {
    if (!Number.isInteger(next) || next < 1 || next === page || (totalPages > 0 && next > totalPages)) return
    setTransactionsState((current) => ({ ...current, status: 'loading', refreshing: Boolean(current.data?.items?.length) }))
    setPageState(next)
  }, [page, totalPages])

  return {
    identityStatus: identity.status,
    identityError: identity.status === 'error',
    retryIdentity: () => setIdentity((current) => ({ ...current, status: 'loading', retry: current.retry + 1 })),
    portfolioStatus: identity.status === 'ready' && portfolioState.status === 'idle' ? 'loading' : portfolioState.status,
    portfolio: portfolioState.data,
    portfolioError: portfolioState.status === 'error',
    portfolioRefreshing: portfolioState.refreshing,
    retryPortfolio: () => setPortfolioState((current) => ({ ...current, status: current.data ? current.status : 'loading', refreshing: Boolean(current.data), retry: current.retry + 1 })),
    transactionsStatus: identity.status === 'ready' && transactionsState.status === 'idle' ? 'loading' : transactionsState.status,
    transactions: transactionsState.data,
    transactionsError: transactionsState.status === 'error',
    transactionsRefreshing: transactionsState.refreshing,
    page,
    totalPages,
    totalElements: transactionsState.data?.totalElements ?? 0,
    setPage,
    retryTransactions: () => setTransactionsState((current) => ({ ...current, status: current.data?.items?.length ? current.status : 'loading', refreshing: Boolean(current.data?.items?.length), retry: current.retry + 1 })),
  }
}
