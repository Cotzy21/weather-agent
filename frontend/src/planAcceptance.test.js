import { describe, it, expect, vi } from 'vitest'
import { createPlanAcceptor } from './planAcceptance.js'

const plan = () => ({ workouts: [{ title: 'A', type: 'STYRKE', content: { blocks: [] } }, { title: 'B', type: 'STYRKE', content: { blocks: [] } }] })
describe('plan acceptance', () => {
  it('retries a lost response with exactly the same batch and clientId', async () => {
    const send = vi.fn().mockRejectedValueOnce(new Error('connection lost')).mockResolvedValueOnce([])
    const newId = vi.fn().mockReturnValue('batch-id')
    const accept = createPlanAcceptor({ send, newId })
    const p = plan()
    await expect(accept(p)).rejects.toThrow('connection lost')
    await accept(p)
    expect(send.mock.calls[0][0]).toBe(send.mock.calls[1][0])
    expect(JSON.parse(send.mock.calls[0][0]).workouts).toHaveLength(2)
    expect(newId).toHaveBeenCalledTimes(1)
  })
  it('shares a concurrent acceptance and starts a new request for an edited plan', async () => {
    const send = vi.fn().mockResolvedValue([])
    const newId = vi.fn().mockReturnValueOnce('first').mockReturnValueOnce('second')
    const accept = createPlanAcceptor({ send, newId })
    const p = plan()
    await Promise.all([accept(p), accept(p)])
    expect(send).toHaveBeenCalledTimes(1)
    await accept({ ...p, workouts: [p.workouts[0]] })
    expect(JSON.parse(send.mock.calls[1][0]).clientId).toBe('second')
  })
})
