package com.example.data

import kotlinx.coroutines.sync.Mutex

/**
 * Global synchronization locks for SDMX Renew.
 *
 * LOCK ACQUISITION HIERARCHY (Deadlock Prevention):
 *
 * To prevent any possibility of deadlock, locks must STRICTLY be acquired in monotonic order:
 *
 * Level 1 (Coarsest): SdmxExecutionEngine.executionMutex
 *   - Protects the long-running 24/7 background renewal cycle (API calls, deletes, creates).
 *
 * Level 2 (Medium): AppLocks.data
 *   - Protects multi-step user database manipulations requested by incoming bot/link commands (Phase 4),
 *     such as ADD_USER with replacement, or coordinating batch operations.
 *   - Can be acquired when no execution cycle is running, or while holding executionMutex (if needed).
 *
 * Level 3 (Finest): LocalDatabase.mutex (internal to LocalDatabase)
 *   - Protects immediate in-memory mutations of _users and atomic file I/O operations (sdmx_users.json).
 *   - Very short lock hold times (milliseconds).
 *   - Never acquires AppLocks.data or executionMutex from within LocalDatabase.
 *
 * By strictly following Level 1 -> Level 2 -> Level 3 and NEVER acquiring backwards, circular
 * wait conditions (deadlocks) are mathematically eliminated.
 */
object AppLocks {
    val data = Mutex()
}
