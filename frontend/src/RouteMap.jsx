import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// Interaktivt kart: klikk legger til et veipunkt (via onAdd). Waypoints tegnes
// som nummererte markører. Før beregning vises en stiplet rett linje mellom dem;
// etter beregning vises den faktiske traséen (route) som en heltrukken linje.
// focus (valgfri) er et mål fra værsøket: kartet sentreres dit og stedet får
// en egen oransje målmarkør.
export default function RouteMap({ waypoints, route = [], onAdd, focus = null }) {
  const elRef = useRef(null)
  const mapRef = useRef(null)
  const layerRef = useRef(null)
  const onAddRef = useRef(onAdd)

  useEffect(() => {
    onAddRef.current = onAdd
  }, [onAdd])

  useEffect(() => {
    if (!elRef.current) return

    if (!mapRef.current) {
      // Zoom-knappene til høyre (à la AllTrails) - panelet ligger oppe til venstre.
      mapRef.current = L.map(elRef.current, { zoomControl: false }).setView([64.5, 12], 4)
      L.control.zoom({ position: 'topright' }).addTo(mapRef.current)
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap-bidragsytere',
        maxZoom: 17,
      }).addTo(mapRef.current)
      layerRef.current = L.layerGroup().addTo(mapRef.current)
      mapRef.current.on('click', (e) => onAddRef.current(e.latlng.lat, e.latlng.lng))
    }

    const layer = layerRef.current
    layer.clearLayers()

    const routePts = route.map((p) => [p.lat, p.lon])
    const wpts = waypoints.map((w) => [w.lat, w.lon])
    if (routePts.length > 1) {
      L.polyline(routePts, { color: '#2f7d4f', weight: 4 }).addTo(layer)
    } else if (wpts.length > 1) {
      L.polyline(wpts, { color: '#2f7d4f', weight: 3, dashArray: '6 6', opacity: 0.7 }).addTo(layer)
    }
    waypoints.forEach((w, i) => {
      L.circleMarker([w.lat, w.lon], {
        radius: 9,
        color: '#225c3a',
        fillColor: '#2f7d4f',
        fillOpacity: 0.95,
        weight: 2,
      })
        .bindTooltip(String(i + 1), { permanent: true, direction: 'center', className: 'wp-label' })
        .addTo(layer)
    })

    if (focus) {
      L.circleMarker([focus.lat, focus.lon], {
        radius: 10,
        color: '#b45309',
        fillColor: '#f59e0b',
        fillOpacity: 0.95,
        weight: 2,
      })
        .bindTooltip(`🎯 ${focus.name}`, { direction: 'top' })
        .addTo(layer)
    }

    setTimeout(() => mapRef.current.invalidateSize(), 0)
  }, [waypoints, route, focus])

  // Nytt mål fra værsøket -> flytt kartet dit (én gang per mål).
  useEffect(() => {
    if (focus && mapRef.current) {
      mapRef.current.setView([focus.lat, focus.lon], 12)
    }
  }, [focus])

  // Når brukeren drar i kartet for å endre størrelsen, må Leaflet få vite det.
  useEffect(() => {
    if (!elRef.current) return undefined
    const ro = new ResizeObserver(() => mapRef.current && mapRef.current.invalidateSize())
    ro.observe(elRef.current)
    return () => ro.disconnect()
  }, [])

  useEffect(() => () => {
    if (mapRef.current) {
      mapRef.current.remove()
      mapRef.current = null
    }
  }, [])

  return <div ref={elRef} className="map route-map" />
}
