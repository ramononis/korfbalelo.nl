// simulationWorker.ts
import { constructCalc, type SimulatorParams, type SimulatorResult } from '@/simulator/SeasonSimulator'

let calc: (() => SimulatorResult) | null = null

self.onmessage = async (e: MessageEvent<{ type: string, payload: SimulatorParams }>) => {
  if (e.data.type === 'start') {
    const payload = e.data.payload as SimulatorParams
    calc = await constructCalc(payload)
  }

  if (!calc) {
    return
  }
  const result = calc()

  // Post the result back to the main thread
  postMessage(result)
}
