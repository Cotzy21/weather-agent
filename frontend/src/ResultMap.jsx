import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// Viser stedene fra rangeringen på et OpenStreetMap-kart. Vinneren (første)
// utheves i rødt, resten i blått. Vi bruker circleMarkers så vi slipper
// Leaflet sitt kjente bundler-problem med marker-ikon-bilder.
export default function ResultMap({ places }) {
  const elRef = useRef(null)
  const mapRef = useRef(null)
  const layerRef = useRef(null)

  useEffect(() => {
    if (!elRef.current) return

    if (!mapRef.current) {
      mapRef.current = L.map(elRef.current, { scrollWheelZoom: false })
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap-bidragsytere',
        maxZoom: 17,
      }).addTo(mapRef.current)
      layerRef.current = L.layerGroup().addTo(mapRef.current)
    }

    const map = mapRef.current
    const layer = layerRef.current
    layer.clearLayers()

    const points = []
    places.forEach((p, i) => {
      if (p.lat == null || p.lon == null) return
      const winner = i === 0
      L.circleMarker([p.lat, p.lon], {
        radius: winner ? 10 : 6,
        color: winner ? '#dc2626' : '#2563eb',
        fillColor: winner ? '#ef4444' : '#3b82f6',
        fillOpacity: 0.85,
        weight: 2,
      })
        .bindPopup(
          `<strong>${p.name}</strong><br/>${p.temp.toFixed(1)} °C · ` +
            `${p.precip.toFixed(1)} mm · ${p.elevation.toFixed(0)} moh`
        )
        .addTo(layer)
      points.push([p.lat, p.lon])
    })

    if (points.length === 1) {
      map.setView(points[0], 9)
    } else if (points.length > 1) {
      map.fitBounds(points, { padding: [30, 30] })
    }
    // Kartet ligger i en flex/animert boks; sikre riktig størrelse etter layout.
    setTimeout(() => map.invalidateSize(), 0)
  }, [places])

  // Rydd opp Leaflet-instansen når komponenten forsvinner.
  useEffect(() => () => {
    if (mapRef.current) {
      mapRef.current.remove()
      mapRef.current = null
    }
  }, [])

  return <div ref={elRef} className="map" />
}
