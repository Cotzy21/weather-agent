import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const NEAR_KM = 5 // turruter innenfor denne avstanden fra valgt sted "lyser opp"

// Grov avstand i km mellom to punkter (haversine).
function distanceKm(aLat, aLon, bLat, bLon) {
  const R = 6371
  const dLat = ((bLat - aLat) * Math.PI) / 180
  const dLon = ((bLon - aLon) * Math.PI) / 180
  const s =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((aLat * Math.PI) / 180) * Math.cos((bLat * Math.PI) / 180) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(s))
}

// Viser stedene fra rangeringen på et OpenStreetMap-kart: vinner i rødt, andre
// blå, turruter grønne. Velger man et sted (selected), sentreres kartet der,
// stedet blir oransje, og turruter nær det stedet lyser opp i oransje.
// circleMarkers brukes for å unngå Leaflet sitt bundler-problem med ikon-bilder.
export default function ResultMap({ places, trails = [], selected = null }) {
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
      const isSelected = selected && p.lat === selected.lat && p.lon === selected.lon
      const winner = i === 0
      L.circleMarker([p.lat, p.lon], {
        radius: isSelected ? 11 : winner ? 10 : 6,
        color: isSelected ? '#c2410c' : winner ? '#dc2626' : '#2563eb',
        fillColor: isSelected ? '#f97316' : winner ? '#ef4444' : '#3b82f6',
        fillOpacity: 0.9,
        weight: 2,
      })
        .bindPopup(
          `<strong>${p.name}</strong><br/>${p.temp.toFixed(1)} °C · ` +
            `${p.precip.toFixed(1)} mm · ${p.elevation.toFixed(0)} moh`
        )
        .addTo(layer)
      points.push([p.lat, p.lon])
    })

    // Turruter. Hvis et sted er valgt, lyser rutene nær det opp i oransje.
    trails.forEach((tr) => {
      if (tr.latitude == null || tr.longitude == null) return
      const near = selected && distanceKm(selected.lat, selected.lon, tr.latitude, tr.longitude) <= NEAR_KM
      L.circleMarker([tr.latitude, tr.longitude], {
        radius: near ? 7 : 5,
        color: near ? '#c2410c' : '#15803d',
        fillColor: near ? '#fb923c' : '#22c55e',
        fillOpacity: 0.9,
        weight: 2,
      })
        .bindPopup(`🥾 <strong>${tr.name}</strong>${tr.operator ? `<br/>${tr.operator}` : ''}`)
        .addTo(layer)
      points.push([tr.latitude, tr.longitude])
    })

    if (selected) {
      map.setView([selected.lat, selected.lon], 11)
    } else if (points.length === 1) {
      map.setView(points[0], 9)
    } else if (points.length > 1) {
      map.fitBounds(points, { padding: [30, 30] })
    }
    // Kartet ligger i en flex/animert boks; sikre riktig størrelse etter layout.
    setTimeout(() => map.invalidateSize(), 0)
  }, [places, trails, selected])

  // Rydd opp Leaflet-instansen når komponenten forsvinner.
  useEffect(() => () => {
    if (mapRef.current) {
      mapRef.current.remove()
      mapRef.current = null
    }
  }, [])

  return <div ref={elRef} className="map" />
}
