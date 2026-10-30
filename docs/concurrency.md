# Concurrency: problems found and how we fixed them

IDs (C1–C12) match `docs/legacy-analysis.md`. Line numbers refer to `docs/legacy/SVFRMS.java`.

## Threads in the system

| | Legacy | Refactored |
|---|---|---|
| Sensor work | 4 × `ChamberThread extends Thread` | `ChamberTask implements Runnable` on a fixed thread pool (`chamber-N`) |
| Device work | 1 × `ControlThread extends Thread` with hand-written `wait/notifyAll` | `QueuedControlDispatcher` worker on a single-thread executor (`control-1`) |
| End of day | 2 raw threads (archive, report) racing each other | `EndOfDayService` runs archive → report → file in order on the main thread |
| Shutdown | `join()` with no timeout | `Future.get(timeout)`, poison-pill drain, `shutdown()` / `awaitTermination` / `shutdownNow()` |

## Before / after

| ID | Problem (type) | Legacy evidence | Fix | Where | Proven by |
|---|---|---|---|---|---|
| C1 | Check-then-act on `HashMap` (race, unsafe collection) | `getChamberState` L48-53 | `ConcurrentHashMap.computeIfAbsent` (atomic) | `ChamberStateStore`, `ChamberSummaryTracker` | `ThreadSafetyStressTest.eachChamberGetsExactlyOneState`; legacy demo shows duplicates. Our first legacy run crashed with an NPE at L146 |
| C2 | `total += x` / `count++` from many threads (lost update) | L74-75, L148-186, L291-292 | `AtomicLong.addAndGet`; alert counter under the `AlertService` lock | `ResourceUsageTracker`, `AlertService` | `resourceTotalsNeverLoseUpdates`; legacy demo: 80,000,000 L expected, **11,368,500 L** counted |
| C3 | Shared `ArrayList` written by 4 threads (unsafe collection) | `alerts.add` L73, `sensorLogs.add` L64 | List guarded by one lock; `ConcurrentLinkedQueue` for the log | `AlertService`, `SensorLog` | `everyRaisedAlertIsCountedAndStored` |
| C4 | Summary counters updated by chamber + control threads (race) | L79-86 | `synchronized` methods + immutable `ChamberSummarySnapshot` | `ChamberSummary` | `chamberSummaryCountsAreExact` |
| C5 | Read-modify-write on chamber fields from two threads; fields not volatile (race + visibility) | L94-124 vs L146-198 | Per-chamber monitor; `adjust(type, fn)` is atomic | `ChamberState` | `chamberStateAdjustIsAtomic` |
| C6 | Report reads under locks that writers never take (false synchronization) | L208-215 | All reads go through one consistent snapshot (`AlertService.snapshot()` under its lock, atomics for totals) | `SnapshotCollector`, `FacilitySnapshot` | invariant `generated == active + archived` asserted while archiving concurrently |
| C7 | Nested `alertLock → reportLock` (latent lock-ordering deadlock) | L208-209, L223-230, plus 2 unused locks L28-29 | **No code path holds two locks at once.** `archiveActive()` releases the alert lock before `ReportLog.add` takes its lock. Listeners are called outside locks | `AlertService`, `AlertArchiver`, `ReportLog` | `archivingWhileRaisingAndReportingNeverDeadlocksAndStaysConsistent` (16 threads, `assertTimeoutPreemptively`) |
| C8 | `catch(Exception e){}` around `wait()` (interrupt swallowed, so the program can hang) | L134, L258 | `BlockingQueue.put/take`; on interrupt, restore the flag and stop. A failing command is logged and the worker continues | `QueuedControlDispatcher` | `producerBlockedOnFullQueueCanBeInterrupted`, `workerKeepsRunningAfterAFailingCommand`; legacy demo shows the producer still blocked after `interrupt()` |
| C9 | Archive and report threads race (non-deterministic output) | L373-389 | Run in a fixed order: archive, then report | `EndOfDayService` | golden tests: "Active Alerts: 0" every time |
| C10 | Raw threads, untimed `join`, no shutdown path | L350-371 | Executors, timeouts, `shutdownNow()` in `finally`, failures reported per chamber | `FacilitySimulation`, `NamedThreadFactory` | `ConcurrentSimulationTest` (×5, 60 s timeout) |
| C11 | `reports` list written by two threads | L215, L233 | `ReportLog` with synchronized access | `ReportLog` | covered by C7 test |
| C12 | Two-line control messages interleaved with other threads' output | L96-97 | Print both lines while holding the stream's monitor | `ConsoleEventPrinter` | manual run |

## The deadlock story (C7 + C8)

There are two deadlock risks in the legacy code:

1. **Lock ordering (latent).** `generateDailyReport()` and `archiveAlerts()` both take `alertLock` and then
   `reportLock`. Today they use the same order, so they happen not to deadlock. But the design invites it.
   The locks are `public static` and non-final, two more locks (`waterPumpLock`, `nutrientLock`) exist unused, and
   the commented-out `Thread.sleep(100)` (L224) shows someone was debugging timing. The first method that
   takes `reportLock → alertLock` gives the classic circular wait: thread A holds alertLock and wants
   reportLock, while thread B holds reportLock and wants alertLock.

2. **Circular wait through the queue (reachable).** `main` waits (`join`) for the chamber threads. A chamber
   thread waits in `addControlRequest` for queue space, and only the `ControlThread` frees space. The
   `ControlThread` calls `getChamberState` too, so the C1 race can throw an NPE in it. If that happens, the
   control thread dies, the queue fills up (5 entries), every chamber thread waits forever, and `main` waits
   forever. Because `InterruptedException` is swallowed, nothing can even cancel them.

**Our rule (documented global lock order).** Locks in the new system, from outermost to innermost:

```
ChamberState (per chamber)  →  ChamberSummary (per chamber)  →  AlertService.lock  →  ReportLog
```

Today **no method holds more than one of these at a time**, which is the strongest guarantee: no nesting
means no cycle and no deadlock. If someone ever needs two, they must take them in the order above. Two more
rules keep it that way:

* never call a listener or other "foreign" code while holding a lock (`AlertService.raise` notifies after
  releasing its lock), and
* never block on the queue while holding a lock (`DeviceResponder` calls `dispatcher.request` from outside any lock).

## Other choices

* **Immutable data crosses threads.** `SensorReading`, `Alert`, `ChamberSummarySnapshot`, `AlertSnapshot`
  and `FacilitySnapshot` are records, safe to share without locks.
* **Simple tools first.** `synchronized` methods where an object guards its own fields, atomics for counters,
  concurrent collections for maps and queues. We did not need `ReentrantLock` or `ReadWriteLock`. Every
  critical section is tiny, so plain monitors are easier to read and to explain.
* **Deterministic mode.** `--sequential` runs everything on one thread with an `ImmediateControlDispatcher`.
  The golden tests use it to prove the output is unchanged, and it is handy for demos.
