import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { login as loginRequest } from '../api/authApi.js'
import { ApiError, NetworkError } from '../api/httpClient.js'
import Banner from '../components/Banner.jsx'
import AuthShowcase from '../components/AuthShowcase.jsx'
import Button from '../components/Button.jsx'
import Carnet from '../components/Carnet.jsx'
import Field from '../components/Field.jsx'
import { useSession } from '../session/SessionContext.jsx'
import './LoginPage.css'

function messageFor(error) {
  if (error instanceof NetworkError) return 'No se pudo conectar con el servidor. Intentá de nuevo.'
  if (error instanceof ApiError && error.status === 401 && error.code === 'invalid_credentials') return 'Usuario o contraseña incorrectos.'
  if (error instanceof ApiError && error.status === 400 && error.code === 'validation_error') return error.message
  return 'Algo salió mal. Intentá de nuevo en unos minutos.'
}

export default function LoginPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { login } = useSession()
  const [form, setForm] = useState({ username: location.state?.username ?? '', password: '' })
  const [info] = useState(location.state?.info ?? null)
  const [error, setError] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (location.state) navigate(location.pathname, { replace: true, state: null })
  }, [location.pathname, location.state, navigate])

  const update = (event) => {
    setForm((current) => ({ ...current, [event.target.name]: event.target.value }))
    setError(null)
  }

  const submit = async (event) => {
    event.preventDefault()
    if (submitting) return
    setSubmitting(true)
    setError(null)
    try {
      const result = await loginRequest(form)
      login({ ...result, username: form.username })
      navigate('/album', { replace: true })
    } catch (requestError) {
      setError(messageFor(requestError))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="page-content auth-page">
      <AuthShowcase
        before="Coleccioná tokens de los"
        highlight="Cracks"
        subtitle="Jugadores de las 5 grandes ligas de Europa. Su valor se mueve con su rendimiento."
      />
      <Carnet variant="login" footer={<>¿Todavía no tenés cuenta? <Link to="/registro">Creá tu carnet de coleccionista</Link></>}>
        <form className="auth-form" onSubmit={submit}>
          {info ? <Banner variant="info">{info}</Banner> : null}
          {error ? <Banner>{error}</Banner> : null}
          <Field id="login-username" name="username" label="Usuario" value={form.username} onChange={update} required autoComplete="username" />
          <Field id="login-password" name="password" label="Contraseña" type="password" value={form.password} onChange={update} required autoComplete="current-password" />
          <Button type="submit" disabled={submitting}>{submitting ? 'Abriendo…' : 'Abrir mi álbum'}</Button>
        </form>
      </Carnet>
    </div>
  )
}
