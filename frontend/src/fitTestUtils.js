// Bygger ekte FIT-filer til tester med Garmins egen koder (kun i tester; ikke en del av appen).
import { Encoder, Profile } from '@garmin/fitsdk'

/**
 * @param {object} o
 * @param {string} o.start ISO-tidspunkt
 * @param {Array<{category:string, sub:number, reps:number, weight:number, type?:string}>} o.sets
 */
export function makeFit({ start, sets, durationS = 3000, calories = 310, avgHr = 118, maxHr = 151, titles = [], workoutName, sport = 'training', subSport = 'strengthTraining' }) {
  const t0 = new Date(start)
  const enc = new Encoder()
  enc.onMesg(Profile.MesgNum.FILE_ID, { type: 'activity', manufacturer: 'garmin', product: 1, timeCreated: t0, serialNumber: 1 })
  if (workoutName) enc.onMesg(Profile.MesgNum.WORKOUT, { wktName: workoutName, sport, numValidSteps: 1 })
  titles.forEach((t, i) => enc.onMesg(Profile.MesgNum.EXERCISE_TITLE, {
    exerciseCategory: t.category, exerciseName: t.sub, wktStepName: [t.name], messageIndex: i,
  }))
  sets.forEach((s, i) => enc.onMesg(Profile.MesgNum.SET, {
    timestamp: new Date(t0.getTime() + i * 60000), startTime: new Date(t0.getTime() + i * 60000), duration: 30,
    repetitions: s.reps, weight: s.weight, setType: s.type ?? 'active',
    category: [s.category], categorySubtype: [s.sub], weightDisplayUnit: 'kilogram', messageIndex: i,
  }))
  enc.onMesg(Profile.MesgNum.SESSION, {
    timestamp: t0, startTime: t0, totalElapsedTime: durationS + 100, totalTimerTime: durationS, sport, subSport,
    totalCalories: calories, avgHeartRate: avgHr, maxHeartRate: maxHr, messageIndex: 0,
  })
  return new Uint8Array(enc.close())
}
