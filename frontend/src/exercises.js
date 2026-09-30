// Øvelsesbiblioteket, gruppert for velgeren. Gruppenavnene oversettes via t().
// `en` er engelske navn i samme rekkefølge; velgeren viser listen for valgt språk,
// og matching (bytt/anbefalinger) tåler begge språk.
export const EXERCISE_GROUPS = [
  {
    group: 'Bryst',
    items: ['Benkpress', 'Skråbenkpress', 'Hantelpress', 'Flies', 'Dips', 'Push-ups'],
    en: ['Bench Press', 'Incline Bench Press', 'Dumbbell Press', 'Chest Fly', 'Dips', 'Push-ups'],
  },
  {
    group: 'Rygg',
    items: ['Markløft', 'Rumensk markløft', 'Nedtrekk', 'Stående roing', 'Sittende roing', 'Pull-ups', 'Chins', 'T-bar roing', 'Face pulls'],
    en: ['Deadlift', 'Romanian Deadlift', 'Lat Pull Downs', 'Bent Over Rows', 'Seated Cable Row', 'Pull-ups', 'Chin-ups', 'T-Bar Row', 'Face Pulls'],
  },
  {
    group: 'Bein',
    items: ['Knebøy', 'Frontbøy', 'Leg press', 'Utfall', 'Bulgarske utfall', 'Leg extension', 'Leg curl', 'Tåhev', 'Hip thrust'],
    en: ['Squat', 'Front Squat', 'Leg Press', 'Lunges', 'Bulgarian Split Squat', 'Leg Extension', 'Leg Curl', 'Calf Raises', 'Hip Thrust'],
  },
  {
    group: 'Skuldre',
    items: ['Skulderpress', 'Sidehev', 'Fronthev', 'Bakre flies', 'Arnold press', 'Opprekk'],
    en: ['Overhead Press', 'Lateral Raises', 'Front Raises', 'Rear Delt Fly', 'Arnold Press', 'Upright Row'],
  },
  {
    group: 'Armer',
    items: ['Bicepscurl', 'Hammercurl', 'Konsentrasjonscurl', 'Triceps pushdown', 'Triceps extension', 'Skullcrushers'],
    en: ['Bicep Curls', 'Hammer Curls', 'Concentration Curls', 'Tricep Pushdown', 'Tricep Extension', 'Skullcrushers'],
  },
  {
    group: 'Mage',
    items: ['Planke', 'Sit-ups', 'Russian twists', 'Hanging leg raise', 'Cable crunch'],
    en: ['Plank', 'Sit-ups', 'Russian Twists', 'Hanging Leg Raise', 'Cable Crunch'],
  },
  {
    group: 'Helkropp',
    items: ['Kettlebell swing', 'Clean and press', 'Thruster', 'Burpees', 'Mountain climbers'],
    en: ['Kettlebell Swing', 'Clean and Press', 'Thruster', 'Burpees', 'Mountain Climbers'],
  },
]

/** Biblioteket på valgt språk: [{ group, items }]. */
export function groupsFor(lang) {
  return EXERCISE_GROUPS.map((g) => ({ group: g.group, items: lang === 'en' ? g.en : g.items }))
}

// Andre øvelser i samme muskelgruppe, på samme språk som øvelsen selv
// (tomt for øvelser utenfor biblioteket).
export function sameGroup(name) {
  const key = name.trim().toLowerCase()
  for (const g of EXERCISE_GROUPS) {
    for (const list of [g.items, g.en]) {
      if (list.some((n) => n.toLowerCase() === key)) {
        return list.filter((n) => n.toLowerCase() !== key)
      }
    }
  }
  return []
}
