/** Bind destructive operations and exports to the account shown when the action began. */
export async function accountHeadersFor(expectedUserId, getSession) {
  const { data, error } = await getSession()
  const session = data?.session
  if (error || !expectedUserId || session?.user?.id !== expectedUserId || !session.access_token) {
    throw new Error('Innloggingen ble endret. Åpne kontosiden på nytt.')
  }
  return { Authorization: `Bearer ${session.access_token}` }
}
