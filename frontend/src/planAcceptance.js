// One stable request per plan snapshot, including after a lost server response.
export function createPlanAcceptor({ send, newId }) {
  const requests = new WeakMap()
  return async function accept(plan) {
    let request = requests.get(plan)
    if (!request) {
      request = {
        body: JSON.stringify({
          clientId: newId(),
          workouts: plan.workouts.map(({ title, type, content, rationale }) => ({
            title, type, content, rationale: rationale || null,
          })),
        }),
        pending: null,
      }
      requests.set(plan, request)
    }
    if (!request.pending) {
      request.pending = Promise.resolve().then(() => send(request.body))
        .finally(() => { request.pending = null })
    }
    return request.pending
  }
}
