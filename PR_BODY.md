Title: refactor(core): improve concurrency, clarity and maintainability across multiple bundles

Summary
- Branch: `assignment1_AbdulAzim`
- Base: `main` (RareZyn/openhab-core)
- Scope: Multiple refactorings across openHAB core bundles to fix concurrency, reduce duplication, extract constants, and add documentation and logging.

1) The addressed issue (1 mark)
- Critical concurrency bug in `GenericItemChannelLinkProvider` (global transaction state causing cross-context contamination).
- Thread-safety risks (non-concurrent collections and global locking) in `FolderObserver` and a non-concurrent live cache in `SemanticsMetadataProvider`.
- Maintainability issues: long methods, duplicated logic, and scattered magic strings in `YamlItemFileConverter` and `DslThingFileConverter`.

2) What I reengineered (1.5 marks)
- `DslThingFileConverter`: Added class-level constants and `ModelThingBuilder` (Builder pattern) to reduce parameter coupling and centralize validation.
- `GenericItemChannelLinkProvider`: Replaced global transaction maps with per-context maps, hardened parsing, added JavaDoc and 10 unit tests covering parsing and concurrency.
- `FolderObserver`: Replaced non-thread-safe collections with concurrent collections, added per-instance `repoLock`, extracted constants and helper validation.
- `YamlItemFileConverter`: Extracted 6 magic strings to constants, added `LOGGER`, replaced silent exception handling, extracted type/dimension helpers and split `buildItemDTO()` into focused submethods.
- `SemanticsMetadataProvider`: Replaced `TreeMap` with `ConcurrentSkipListMap` and returned immutable snapshots; replaced non-standard `getFirst()` with standard `get(0)` and extracted common relation logic.

3) Reengineering strategy / approach used (1.5 marks)
- Strategy: Implementation-level rework (no public API changes), incremental and test-driven.
- Techniques: Extract method/constant, Builder pattern, per-context state isolation, use of concurrent collections, add logging and JavaDoc.
- Verification: Ran bundle-level unit tests and applied Spotless formatting after changes; all affected bundle tests passed locally.

4) Impact of changes (1 mark)
- Correctness: Fixed a concurrency bug that could cause data corruption in multi-context updates.
- Maintainability: Reduced cognitive complexity (shorter methods, helper functions), centralized configuration via constants, and improved documentation.
- Risk: Low — changes are internal; public behavior preserved and covered by tests.

Files changed (high level)
- bundles/org.openhab.core.model.thing/.../DslThingFileConverter.java
- bundles/org.openhab.core.model.thing/.../GenericItemChannelLinkProvider.java (+ tests)
- bundles/org.openhab.core.model.core/.../FolderObserver.java
- bundles/org.openhab.core.model.yaml/.../YamlItemFileConverter.java
- bundles/org.openhab.core.semantics/.../SemanticsMetadataProvider.java

Notes
- All changes are on branch `assignment1_AbdulAzim` in this fork (RareZyn/openhab-core).
- I ran unit tests for affected bundles locally and applied code formatting.
- If you want, I can squash and rebase before creation, or open the PR as-is for review.
