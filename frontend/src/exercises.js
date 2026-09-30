// Øvelsesbiblioteket, gruppert for velgeren. Gruppenavnene oversettes via t().
export const EXERCISE_GROUPS = [
  { group: 'Bryst', items: ['Benkpress', 'Skråbenkpress', 'Hantelpress', 'Flies', 'Dips', 'Push-ups'] },
  { group: 'Rygg', items: ['Markløft', 'Rumensk markløft', 'Nedtrekk', 'Stående roing', 'Sittende roing', 'Pull-ups', 'Chins', 'T-bar roing', 'Face pulls'] },
  { group: 'Bein', items: ['Knebøy', 'Frontbøy', 'Leg press', 'Utfall', 'Bulgarske utfall', 'Leg extension', 'Leg curl', 'Tåhev', 'Hip thrust'] },
  { group: 'Skuldre', items: ['Skulderpress', 'Sidehev', 'Fronthev', 'Bakre flies', 'Arnold press', 'Opprekk'] },
  { group: 'Armer', items: ['Bicepscurl', 'Hammercurl', 'Konsentrasjonscurl', 'Triceps pushdown', 'Triceps extension', 'Skullcrushers'] },
  { group: 'Mage', items: ['Planke', 'Sit-ups', 'Russian twists', 'Hanging leg raise', 'Cable crunch'] },
  { group: 'Helkropp', items: ['Kettlebell swing', 'Clean and press', 'Thruster', 'Burpees', 'Mountain climbers'] },
]

export const EXERCISES = EXERCISE_GROUPS.flatMap((g) => g.items)
