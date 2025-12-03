**Issue Addressed: #12 Synchronous, Blocking I/O**
**The Problem:** The UpnpAddonFinder processes every remoteDeviceAdded event synchronously. If 50 devices announce themselves at once (a "burst"), the main thread is blocked processing device #1, then #2, then #3. This is a classic "Synchronous I/O" bottleneck.

**The Solution (Reengineering):** We will convert this to Asynchronous Batch Processing. Instead of blocking the listener thread to process logic, we instantly queue the event and process it later in a background thread.