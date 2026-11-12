import { expect, it } from 'vitest'
import { accountHeadersFor } from './accountSession.js'

it('refuses to use the token of a different account for a pending action', async () => {
  await expect(accountHeadersFor('first', async () => ({ data: { session: { user: { id: 'second' }, access_token: 'second-token' } } })))
    .rejects.toThrow('Innloggingen ble endret')
})

it('refuses a missing or failed session lookup', async () => {
  for (const result of [{ data: { session: null } }, { error: new Error('failed') }]) {
    await expect(accountHeadersFor('first', async () => result)).rejects.toThrow('Innloggingen ble endret')
  }
})

it('captures the token only for the intended account', async () => {
  expect(await accountHeadersFor('first', async () => ({ data: { session: { user: { id: 'first' }, access_token: 'first-token' } } })))
    .toEqual({ Authorization: 'Bearer first-token' })
})
