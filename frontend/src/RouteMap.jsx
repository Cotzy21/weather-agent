import { useEffect, useRef } from 'react'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

// Interaktivt kart: klikk legger til et veipunkt (via onAdd), og punktene
// tegnes som nummererte markører bundet sammen med en linje.
export default function RouteMap({ waypoints, onAdd }) {
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
      mapRef.current = L.map(elRef.current).setView([64.5, 12], 4)
      L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap-bidragsytere',
        maxZoom: 17,
      }).addTo(mapRef.current)
      layerRef.current = L.layerGroup().addTo(mapRef.current)
      mapRef.current.on('click', (e) => onAddRef.current(e.latlng.lat, e.latlng.lng))
    }

    const layer = layerRef.current
    layer.clearLayers()

    const pts = waypoints.map((w) => [w.lat, w.lon])
    if (pts.length > 1) {
      L.polyline(pts, { color: '#2f7d4f', weight: 4 }).addTo(layer)
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

    setTimeout(() => mapRef.current.invalidateSize(), 0)
  }, [waypoints])

  useEffect(() => () => {
    if (mapRef.current) {
      mapRef.current.remove()
      mapRef.current = null
    }
  }, [])

  return <div ref={elRef} className="map route-map" />
}
