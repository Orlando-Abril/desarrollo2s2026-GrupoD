import { useEffect, useState } from 'react'
import { getCurrentAccount } from '../../api/portfolioApi.js'

// Rol de la cuenta logueada según /auth/me: null mientras carga, 'UNKNOWN' si no se pudo saber.
// Solo decide qué se muestra; el back igual rechaza con 403 lo que no corresponde.
export default function useAccountRole() {
  const [role, setRole] = useState(null)

  useEffect(() => {
    const controller = new AbortController()
    getCurrentAccount({ signal: controller.signal }).then((account) => {
      if (!controller.signal.aborted) setRole(account.role)
    }).catch(() => { if (!controller.signal.aborted) setRole('UNKNOWN') })
    return () => controller.abort()
  }, [])

  return role
}
