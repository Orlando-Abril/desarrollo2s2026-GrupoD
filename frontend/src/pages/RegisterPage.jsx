import { useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { Link, useNavigate } from 'react-router-dom'
import { login as loginRequest, register as registerRequest } from '../api/authApi.js'
import { ApiError, NetworkError } from '../api/httpClient.js'
import Banner from '../components/Banner.jsx'
import AuthShowcase from '../components/AuthShowcase.jsx'
import Button from '../components/Button.jsx'
import Carnet from '../components/Carnet.jsx'
import Field from '../components/Field.jsx'
import { useSession } from '../session/SessionContext.jsx'
import './RegisterPage.css'

function messageFor(error) {
  if (error instanceof NetworkError) return 'No se pudo conectar con el servidor. Intentá de nuevo.'
  if (error instanceof ApiError && error.status === 409 && error.code === 'duplicate_user') return 'El usuario o el email ya está registrado.'
  if (error instanceof ApiError && error.status === 400 && error.code === 'validation_error') return error.message
  return 'Algo salió mal. Intentá de nuevo en unos minutos.'
}

function addFieldError(errors, field, message) {
  errors[field] = message
}

export default function RegisterPage() {
  const navigate = useNavigate()
  const { login } = useSession()
  const [form, setForm] = useState({ username: '', email: '', password: '' })
  const [errors, setErrors] = useState({})
  const [banner, setBanner] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const usernameRef = useRef(null)
  const emailRef = useRef(null)
  const passwordRef = useRef(null)

  const refs = { username: usernameRef, email: emailRef, password: passwordRef }

  const update = (event) => {
    const { name, value } = event.target
    setForm((current) => ({ ...current, [name]: value }))
    setErrors((current) => ({ ...current, [name]: undefined }))
    setBanner(null)
  }

  const validate = () => {
    const next = {}
    if (!form.username.trim()) addFieldError(next, 'username', 'Obligatorio.')
    if (!form.email) addFieldError(next, 'email', 'Obligatorio.')
    else if (emailRef.current?.validity.typeMismatch) addFieldError(next, 'email', 'Ingresá un email válido.')
    if (!form.password) addFieldError(next, 'password', 'Obligatorio.')
    else if (form.password.length < 8) addFieldError(next, 'password', 'Mínimo 8 caracteres.')
    setErrors(next)
    const first = ['username', 'email', 'password'].find((field) => next[field])
    refs[first]?.current?.focus()
    return Object.keys(next).length === 0
  }

  const submit = async (event) => {
    event.preventDefault()
    if (submitting || !validate()) return
    setSubmitting(true)
    setBanner(null)
    try {
      await registerRequest(form)
      try {
        const result = await loginRequest({ username: form.username, password: form.password })
        flushSync(() => login({ ...result, username: form.username }))
        navigate('/album', {
          replace: true,
          state: { toast: `¡Bienvenida/o, ${form.username}! Tu álbum ya está abierto.` },
        })
      } catch {
        navigate('/ingresar', {
          replace: true,
          state: {
            username: form.username,
            info: 'Cuenta creada. Ingresá con tu contraseña.',
          },
        })
      }
    } catch (requestError) {
      setBanner(messageFor(requestError))
      setSubmitting(false)
    }
  }

  return (
    <div className="page-content auth-page register-page">
      <AuthShowcase
        before="Tu álbum"
        highlight="Arranca"
        after="con 1.000 créditos"
        subtitle="Creá tu cuenta y empezá a armar tu colección."
      />
      <Carnet variant="register" footer={<>¿Ya tenés tu carnet? <Link to="/ingresar">Ingresá</Link></>}>
        <form className="auth-form" onSubmit={submit} noValidate>
          {banner ? <Banner>{banner}</Banner> : null}
          <Field ref={usernameRef} id="register-username" name="username" label="Usuario" value={form.username} onChange={update} error={errors.username} autoComplete="username" />
          <Field ref={emailRef} id="register-email" name="email" label="Email" type="email" value={form.email} onChange={update} error={errors.email} autoComplete="email" />
          <Field ref={passwordRef} id="register-password" name="password" label="Contraseña" type="password" value={form.password} onChange={update} hint="Mínimo 8 caracteres." error={errors.password} autoComplete="new-password" />
          <Button type="submit" disabled={submitting}>{submitting ? 'Creando…' : 'Crear mi carnet'}</Button>
        </form>
      </Carnet>
    </div>
  )
}
