import { useLayoutEffect, useRef } from 'react'
import gsap from 'gsap'

/**
 * Staggered inntoning av alle [data-reveal]-elementer under ref-en.
 * Kjøres på mount (og når deps endres) – gir "siden bygger seg opp"-følelsen.
 */
export function useReveal(deps = []) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const targets = el.querySelectorAll('[data-reveal]')
    if (!targets.length) return undefined
    const ctx = gsap.context(() => {
      gsap.fromTo(
        targets,
        { opacity: 0, y: 22 },
        {
          opacity: 1,
          y: 0,
          duration: 0.6,
          stagger: 0.08,
          ease: 'power3.out',
          clearProps: 'opacity,transform',
        },
      )
    }, el)
    return () => ctx.revert()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps)
  return ref
}

/** Myk fane-overgang: fade + glid når `key` endrer seg. */
export function useTabTransition(key) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const tween = gsap.fromTo(
      el,
      { opacity: 0, y: 14, scale: 0.995 },
      { opacity: 1, y: 0, scale: 1, duration: 0.45, ease: 'power2.out', clearProps: 'all' },
    )
    return () => tween.kill()
  }, [key])
  return ref
}

/** Teller et tall opp fra 0 – for dashboard-statistikk. */
export function useCountUp(value) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return undefined
    const state = { n: 0 }
    const tween = gsap.to(state, {
      n: value,
      duration: 1.1,
      ease: 'power2.out',
      onUpdate: () => { el.textContent = Math.round(state.n) },
    })
    return () => tween.kill()
  }, [value])
  return ref
}

/** Liten "pop" på et element – f.eks. når noe legges til favoritter. */
export function pop(el) {
  if (!el) return
  gsap.fromTo(el, { scale: 0.6 }, { scale: 1, duration: 0.45, ease: 'back.out(2.5)' })
}
