***org.openhab.core.config.discovery.addon***

While config.discovery is responsible for finding Hardware Devices (Things) like lights and sensors, config.discovery.addon is responsible for finding Software Extensions (Add-ons) that can be installed into OpenHAB. The org.openhab.core.config.discovery.addon bundle is the bridge between Add-on Sources (Files, Internet) and the User Interface (Settings > Add-on Store). It ensures that when a user searches for a binding, the system knows where to find it.

1. ***The Issue to Address (Legacy Characteristic)***
We are addressing Legacy ***Characteristic #12: Synchronous, Blocking I/O / Resource Contention.***
•	The Problem: In the current implementation, the method getSuggestedAddons() wraps the entire execution logic inside a synchronized (addonFinders) block.
•	Why this is bad (The Bottleneck):
o	When the UI asks "What addons do you suggest?", this method locks the list.
o	It then iterates through every finder (USB, mDNS, IP, etc.) and calls f.getSuggestedAddons().
o	If one finder is slow (e.g., checking a network resource or a slow USB bus), it holds the lock.
o	While the lock is held, no other thread can add or remove a finder (addAddonFinder blocks). The entire service effectively freezes until the slowest finder finishes. This is Coarse-Grained Locking, a classic performance anti-pattern.

2. ***The Reengineering Solution***
We will apply the Rework Strategy (specifically Concurrency Refactoring). We will move from "Coarse-Grained Locking" to "Non-Blocking Concurrency" using CopyOnWriteArrayList.
The Implementation Plan
**Step A:** Change the Data Structure Instead of using a standard ArrayList which requires manual synchronization, we switch to a CopyOnWriteArrayList. This is thread-safe by design and optimized for scenarios where "Reads" (getting suggestions) happen much more often than "Writes" (installing a finder).
**Step B:** Remove the synchronized Blocks We will strip out the synchronized (addonFinders) blocks. This allows getSuggestedAddons() to iterate over a "snapshot" of the finders without blocking the main system.
