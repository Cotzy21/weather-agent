/** One outline icon family for navigation and actions. Text labels carry meaning. */
const paths = {
  home: 'm3 10 9-7 9 7M5 9v12h14V9M9 21v-7h6v7',
  training: 'm6 6 12 12M3 8l5-5M2 5l3-3M16 21l5-5M19 22l3-3M4 12l8-8M12 20l8-8',
  food: 'M5 3v6a3 3 0 0 0 6 0V3M8 3v18M19 3c-3 2-4 6-4 10h4M19 3v18',
  recovery: 'M20 13a8 8 0 1 1-9-9 6 6 0 0 0 9 9Z',
  weather: 'M8 17H7a4 4 0 1 1 1-8 5 5 0 0 1 9 2 3 3 0 1 1 0 6h-1M10 19l-1 2M15 19l-1 2M4 3l1 2M12 2v2',
  route: 'M6 4a2 2 0 1 1 0 4 2 2 0 0 1 0-4Zm12 12a2 2 0 1 1 0 4 2 2 0 0 1 0-4ZM8 6h7a4 4 0 0 1 0 8H9a3 3 0 0 0 0 6h7',
  mountain: 'm2 19 7-13 5 9 3-5 5 9H2ZM7 10l2 2 2-2',
  more: 'M5 12h.01M12 12h.01M19 12h.01',
  user: 'M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2',
  close: 'm6 6 12 12M6 18 18 6',
  arrow: 'M4 12h16m-6-6 6 6-6 6',
  back: 'M20 12H4m6-6-6 6 6 6',
  chevron: 'm9 5 7 7-7 7',
  plus: 'M12 5v14M5 12h14',
  play: 'm8 4 12 8-12 8V4Z',
  trend: 'm3 17 6-6 4 4 8-10M15 5h6v6',
  edit: 'm15 5 4 4M4 20l4-1L20 7l-4-4L4 15v5Z',
  calendar: 'M5 5h14a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2ZM7 3v4M17 3v4M3 11h18',
  clock: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM12 7v5l3 2',
  spark: 'm12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5L12 3Z',
  import: 'M12 3v12m-5-5 5 5 5-5M4 16v5h16v-5',
  globe: 'M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0ZM3 12h18M12 3c5 5 5 13 0 18-5-5-5-13 0-18Z',
  sun: 'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0ZM12 2v2M12 20v2M2 12h2M20 12h2M5 5l1 1M18 18l1 1M5 19l1-1M18 6l1-1',
  check: 'm5 12 4 4L19 6',
  weight: 'M8 7a4 4 0 1 1 8 0M6 7h12l3 14H3L6 7Z',
}

export default function Icon({ name, size = 20, className = '' }) {
  return <svg className={`ui-icon ${className}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={1.7} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={paths[name] ?? paths.mountain} /></svg>
}
