**Issue Addressed: #12 Synchronous, Blocking I/O**
**The Problem:** The method removeUsbSerialDiscovery is declared as synchronized.

**Why it fits #12:** The synchronized keyword creates a Blocking Lock. If one thread is removing a discovery service, all other threads (including the UI) are blocked from accessing this object. This is "Blocking I/O" behavior applied to object access.

**The Solution (Reengineering):** Remove the synchronized keyword and rely on Non-Blocking Concurrent Collections (CopyOnWriteArraySet) which are already present in the code but were being utilized inefficiently with redundant locking.