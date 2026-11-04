import { useLayoutEffect, useRef } from 'react'
import gsap from 'gsap'

// matchMedia also responds when the user changes the OS preference mid-session.
export function withMotion(scope, animate, immediate = () => {}) {
  const media = gsap.matchMedia()
  media.add({ motion: '(prefers-reduced-motion: no-preference)', reduced: '(prefers-reduced-motion: reduce)' }, (ctx) => {
    return ctx.conditions.reduced ? immediate() : animate()
  }, scope)
  return () => media.revert()
}

/**
 * Stabil referanse for eksisterende [data-reveal]-områder.
 * Innhold vises umiddelbart; bevegelse brukes ved brukerhandlinger.
 */
export function useReveal(deps = []) {
  const ref = useRef(null)
  // App screens stay readable immediately. Motion is reserved for user feedback.
  void deps
  return ref
}

/** Myk fane-overgang: fade + glid når `key` endrer seg. */
export function useTabTransition(key) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    const el = ref.current
    if (!el) return undefined
    return withMotion(el, () => { gsap.fromTo(
      el,
      { opacity: 0, y: 14, scale: 0.995 },
      { opacity: 1, y: 0, scale: 1, duration: 0.32, ease: 'power2.out', clearProps: 'opacity,transform' },
    ) })
  }, [key])
  return ref
}

/** Viser den faktiske dashboard-verdien uten en kunstig opptelling fra null. */
export function useCountUp(value) {
  const ref = useRef(null)
  useLayoutEffect(() => {
    if (ref.current) ref.current.textContent = Math.round(value)
  }, [value])
  return ref
}

/** Liten "pop" på et element – f.eks. når noe legges til favoritter. */
export function pop(el) {
  if (!el || window.matchMedia('(prefers-reduced-motion: reduce)').matches) return
  gsap.fromTo(el, { scale: 0.85 }, { scale: 1, duration: 0.38, ease: 'back.out(1.8)', overwrite: true, clearProps: 'transform' })
}

/** Bars grow from their baseline; their real heights stay in React/CSS. */
export function useBarGrowth(key) {
  const ref = useRef(null)
  void key
  return ref
}
