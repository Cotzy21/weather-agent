import L from 'leaflet'

// Kartverket's documented WMTS endpoint uses {z}/{y}/{x}, not XYZ {z}/{x}/{y}.
export const MAP_SOURCES = {
  standard: { url: 'https://cache.kartverket.no/v1/wmts/1.0.0/topo/default/webmercator/{z}/{y}/{x}.png', attribution: '© <a href="https://www.kartverket.no/">Kartverket</a>', maxNativeZoom: 18 },
  terrain: { url: 'https://cache.kartverket.no/v1/wmts/1.0.0/toporaster/default/webmercator/{z}/{y}/{x}.png', attribution: '© <a href="https://www.kartverket.no/">Kartverket</a>', maxNativeZoom: 18 },
  satellite: { url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}', attribution: 'Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community', maxNativeZoom: 19 },
}

export function addMapLayers(map, { selectable = false } = {}) {
  const layers = Object.fromEntries(Object.entries(MAP_SOURCES).map(([key, source]) => [key,
    L.tileLayer(source.url, { attribution: source.attribution, maxZoom: 19, maxNativeZoom: source.maxNativeZoom }),
  ]))
  let active = layers.standard
  let timer
  let errors = 0
  let loaded = false
  let switched = false
  let outsideCoverage = false
  let stopped = false
  const status = L.control({ position: 'bottomleft' })
  const element = L.DomUtil.create('div', 'map-status')
  const message = L.DomUtil.create('span', '', element)
  const retry = L.DomUtil.create('button', '', element)
  element.setAttribute('role', 'status')
  element.setAttribute('aria-live', 'polite')
  retry.type = 'button'
  retry.textContent = 'Prøv kartet igjen'
  status.onAdd = () => element
  status.addTo(map)
  L.DomEvent.disableClickPropagation(element)
  L.DomEvent.disableScrollPropagation(element)
  function show(text, canRetry = false) {
    message.textContent = text
    element.hidden = !text
    retry.hidden = !canRetry
  }
  function arm() {
    clearTimeout(timer)
    errors = 0
    loaded = false
    timer = setTimeout(() => { if (!loaded) fail() }, 8000)
  }
  function fail() {
    clearTimeout(timer)
    if (stopped) return
    if (active !== layers.satellite) {
      map.removeLayer(active)
      active = layers.satellite
      switched = true
      active.addTo(map)
      show('Kartverket svarte ikke. Viser satellittkart.', true)
      arm()
    } else {
      show('Kartet kan ikke lastes. Sjekk nettet og prøv igjen.', true)
    }
  }
  for (const layer of Object.values(layers)) {
    layer.on('tileerror', () => { if (!stopped && active === layer && ++errors >= 4) fail() })
    layer.on('tileload', () => {
      if (active !== layer || stopped) return
      loaded = true
      clearTimeout(timer)
      show(switched ? 'Viser satellittkart. Prøv Kartverket igjen når nettet fungerer.' : '', switched)
    })
    layer.on('loading', () => { if (active === layer && !stopped) arm() })
  }
  const change = (event) => {
    active = event.layer
    outsideCoverage = false
    switched = false
    show('Laster kart …')
    arm()
  }
  const onRetry = () => {
    map.removeLayer(active)
    active = layers.standard
    switched = false
    show('Laster kart …')
    active.addTo(map)
    active.redraw()
    arm()
  }
  L.DomEvent.on(retry, 'click', onRetry)
  map.on('baselayerchange', change)
  // Outside mainland Norway, the existing worldwide imagery layer gives coverage.
  const outsideNorway = () => {
    const { lat, lng } = map.getCenter()
    if ((lat < 57 || lat > 72 || lng < 3 || lng > 34) && active !== layers.satellite) {
      map.removeLayer(active)
      active = layers.satellite
      outsideCoverage = true
      switched = false
      active.addTo(map)
      show('Viser satellittkart utenfor Norge.')
      arm()
    } else if (outsideCoverage && lat >= 57 && lat <= 72 && lng >= 3 && lng <= 34) {
      map.removeLayer(active)
      active = layers.standard
      outsideCoverage = false
      active.addTo(map)
      show('Laster kart …')
      arm()
    }
  }
  map.on('moveend', outsideNorway)
  if (selectable) L.control.layers({ Kartverket: layers.standard, Turkart: layers.terrain, Satellite: layers.satellite }, null, { position: 'topright' }).addTo(map)
  show('Laster kart …')
  active.addTo(map)
  arm()
  map.once('unload', () => {
    stopped = true
    clearTimeout(timer)
    map.off('baselayerchange', change)
    map.off('moveend', outsideNorway)
    L.DomEvent.off(retry, 'click', onRetry)
  })
  return layers
}
