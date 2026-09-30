// Leaflet-popups tar HTML-tekst. Navn på topper, stier og operatører kommer fra OpenStreetMap, som
// hvem som helst kan redigere, så alt som ikke er våre egne tall må escapes før det settes inn.
const MAP = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }

export function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, (c) => MAP[c])
}
