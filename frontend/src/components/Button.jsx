import './Button.css'

export default function Button({ variant = 'primary', className = '', ...props }) {
  return <button className={`button button--${variant} ${className}`.trim()} {...props} />
}
