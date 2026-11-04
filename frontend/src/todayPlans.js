import { dayIndex, localIso, matchesPlan } from './trainingStats.js'

/** Same remaining-plan list for the weekly calendar and the main start action. */
export function remainingPlansToday(plans, workouts, date = new Date()) {
  const weekday = (date.getDay() + 6) % 7
  const logged = workouts.filter(workout => workout.date === localIso(date))
  const timeRank = value => {
    const time = (value ?? '').toLowerCase()
    if (/morg|morn|\bam\b/.test(time)) return 0
    if (/kveld|even|night|\bpm\b/.test(time)) return 2
    return 1
  }
  return plans.filter(plan => dayIndex(plan.content?.day) === weekday)
    .filter(plan => !logged.some(workout => matchesPlan(workout, plan)))
    .sort((a, b) => timeRank(a.content?.time) - timeRank(b.content?.time))
}
