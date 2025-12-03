1. **Issue Addressed**
Legacy **Characteristic #9: Resource Management Issues** (specifically Unsafe Shared Memory Access).

**The Problem:** 
The legacy code used a standard HashSet (foundDevices) to store discovered devices.
**The Conflict:** 
This set is a Shared Resource accessed by two different threads:

**Network Thread:**
Adds/Removes devices when packets arrive.
**UI Thread:**
Iterates the set to find suggestions when the user opens the menu.

**The Failure Mode:** If a device is discovered while the user is looking at the list, the system throws a ConcurrentModificationException and crashes the discovery process.

2. **What We Reengineered**
We applied the Rework Strategy (Thread Safety).

**Approach:** We replaced the non-thread-safe collection with a concurrent one.

**The Change:**
**Before:** private final Set<SddpDevice> foundDevices = new HashSet<>();
**After:** private final Set<SddpDevice> foundDevices = ConcurrentHashMap.newKeySet();

**Why this is "Engineering":** we analyzed the concurrency model and identified a race condition in the resource management. we replaced the underlying data structure to guarantee atomic operations without needing complex blocking locks.