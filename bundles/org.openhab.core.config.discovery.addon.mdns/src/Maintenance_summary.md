Alligns with our narrative of fixing "Blocking I/O" and "Performance" issues, specifically targeting Event Flooding from
 "noisy" mDNS networks.

1. ***The Issue to Address***
***The Problem:*** The MDNSAddonFinder listens for mDNS service events. When a device appears on the network, serviceAdded() or serviceResolved() is called.

***The Bottleneck:*** On a network with many IoT devices (or during startup when everything announces itself), these callbacks fire rapidly.
- Each event triggers addService(), which updates a ConcurrentHashMap.
- While ConcurrentHashMap is thread-safe, if you have 50 devices announcing themselves in 1 second, you are performing 50 individual write operations and potential UI notifications.

***Why it's Legacy:*** It processes every single event immediately (Reactive), rather than batching them for efficiency.

2. ***The Reengineering Solution***
We will apply the Rework Strategy (Batching/Buffering). We will introduce a "Debounced Batch Processor".
***Logic Change:*** Instead of Event $\rightarrow$ Process, we will queue events and process them in chunks (e.g., every 500ms).
***Impact:*** Reduces CPU context switching and allows the system to handle "bursts" of discovery traffic efficiently.