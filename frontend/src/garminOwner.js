import { Capacitor, registerPlugin } from '@capacitor/core'

const GarminOwner = registerPlugin('GarminOwner')

export function hasPrivateGarmin() {
  return Capacitor.getPlatform() === 'ios' && Capacitor.isPluginAvailable('GarminOwner')
}

export function openPrivateGarmin(owner, english) {
  return GarminOwner.open({ owner, english })
}

export async function erasePrivateGarmin(owner) {
  if (hasPrivateGarmin()) await GarminOwner.erase({ owner })
}
