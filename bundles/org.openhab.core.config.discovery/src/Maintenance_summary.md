***org.openhab.core.config.discovery***


***Issue Addressed (From Final Report)***
We are addressing Legacy System Characteristic ***#15: Uncontrolled Event Propagation (Event Storms)*** and ***#16: Excessive Disk I/O (as defined in the improvised Report section).***
The Problem: In the provided file AbstractDiscoveryService.java, the method thingDiscovered() is designed to immediately notify all listeners whenever a binding finds a device.
Legacy Behavior: There is no rate-limiting. If a device (like a UPnP media server or mDNS device) broadcasts its existence every 500ms, the AbstractDiscoveryService triggers a "Discovery Event" every 500ms.
Consequence: This floods the Event Bus and triggers the Inbox listener to write to the JSONDB disk repeatedly, causing the "Excessive Disk I/O" characteristic mentioned in the report.

***What We Reengineered (The Implementation)***
We applied the Rework Strategy (Alteration) to the AbstractDiscoveryService.java file. We introduced a Traffic Shaping / Throttling Layer directly into the core abstract class.
A.	***New Fields Added (The Cache):*** We added a thread-safe cache to track the "Last Seen" timestamp of every device to enable temporal comparison.
B.	***Logic Alteration (The Algorithm):*** We modified the thingDiscovered method. This represents a change in the Control Flow of the application (as discussed in Chapter 6 Impact Analysis ).
C.	***Cleanup (Resource Management):*** To prevent memory leaks (Legacy Characteristic #9 in your report ), we ensure the cache is cleared when a thing is removed

