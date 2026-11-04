import { useLayoutEffect, useRef } from 'react'
import { createPortal } from 'react-dom'

/** Native modal semantics keep the background inert and keyboard focus inside the sheet. */
export default function Sheet({ children, label, className = 'sheet', onClose, role = 'dialog' }) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    const previous = document.activeElement
    const dialog = ref.current
    dialog.showModal()
    return () => {
      dialog.close()
      const destination = previous?.isConnected ? previous : document.getElementById('main-content')
      destination?.focus?.({ preventScroll: true })
    }
  }, [])
  return createPortal(<dialog ref={ref} className="sheet-dialog" role={role} aria-label={label}
    onCancel={event => { event.preventDefault(); onClose?.() }}
    onClick={event => { if (event.target === event.currentTarget) onClose?.() }}>
    <div className={className}>{children}</div>
  </dialog>, document.body)
}
