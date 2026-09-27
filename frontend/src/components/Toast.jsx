import { useEffect } from 'react'
import './Toast.css'

export default function Toast({ children, onDismiss }) {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss?.(), 4000)
    return () => clearTimeout(timer)
  }, [onDismiss])

  return <output className="toast">{children}</output>
}
