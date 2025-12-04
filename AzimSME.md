PR Title

Refactor generateFileFormat() and Add Documentation for DslThingFileConverter

PR Description
1. Addressed Issue

DSL Thing UIDs contain unnecessary double quotes when a segment includes a hyphen.

Methods in DslThingFileConverter were missing proper documentation, making maintenance difficult.

Error logging was weak (only e.getMessage()), and charset handling used platform defaults, risking inconsistent output.

2. What Was Reengineered / Improved
A. generateFileFormat() Refactoring

Encoding Safety: Replaced platform-default String conversion with explicit UTF-8.

Precompiled Regex: Introduced a static Pattern for removing quotes from Thing UIDs, improving performance.

Helper Method: Extracted removeQuotesFromUID() to separate responsibilities and improve testability.

Logging: Updated to log full exception stack trace, not just the message.

B. Added JavaDoc / Comments

Added class-level documentation explaining purpose, responsibilities, and thread-safety.

Added method-level JavaDoc for all public methods, including generateFileFormat(), buildModelThing(), buildModelProperty(), and removeQuotesFromUID().

Added comments for tricky or non-obvious logic (e.g., regex, Double-to-BigDecimal conversion, tree recursion).

3. Reengineering Strategy / Approach

Refactoring: Simplified generateFileFormat() logic, extracted helper methods, precompiled regex.

Documentation: Systematically added JavaDoc and inline comments for maintainability.

Error Handling Improvement: Enhanced logging to include full stack traces.

Resource Safety: Ensured UTF-8 charset for consistent, platform-independent output.

4. Impact of Changes

DSL Thing files are generated consistently across platforms.

Performance improved slightly by reusing regex.

Code readability, maintainability, and testability significantly improved.

Future developers can understand methods quickly due to added documentation.

Logging now provides full stack traces for easier debugging.

Fully backward compatible; no external API changes.

5. Legacy System Characteristics Addressed
Characteristic	How Addressed
#1 Poor Documentation / Missing JavaDoc	Added JavaDoc and comments for class, public, and important private methods.
#2 Weak or Generic Error Handling	Improved logging of exceptions to include full stack trace.
#7 Silent Failures / No Logging	Prevented silent failures by logging all IO exceptions properly.
#9 Resource Management Issues	Explicitly used UTF-8 charset instead of platform default.
#10 Poor Architecture / Single Responsibility Violations	Extracted quote-removal logic into a helper method, simplifying generateFileFormat().