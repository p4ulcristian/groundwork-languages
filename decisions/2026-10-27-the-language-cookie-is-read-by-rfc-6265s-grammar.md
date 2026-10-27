# The language cookie is read by RFC 6265's grammar

> The user's decision, 2026-10-27 (keep groundwork minimal; replace the Cookie header read by a split with ring-core's parsing where ring is on the library's path, otherwise one correct RFC 6265 parse with tests). Since v0.4.4. Changes in part [A request's language: the prefix, the cookie, Accept-Language, the default](2026-10-23-a-requests-language-and-the-defaults-addresses.md): how the cookie is read.

**Decision:** the cookie `:cookie-name` names is read from the Cookie header by RFC 6265's grammar (4.2.1): pairs split by `;`, the spaces and tabs around each taken off; a pair is `name=value` split at its first `=`, the name a token, the value cookie-octets (ASCII, no space, `"`, `,`, `;` or `\`) or cookie-octets in double quotes, read without them; a pair that isn't one is left out and the rest read; a name twice is its first with a value (the browser lists the one for the longest path first), an empty value none. Its value is still taken only when it is one of the languages that show.

**Why:** the split kept a quoted value's quotes (`lang="de"` was no language) and took any text after `=`. ring-core's parsing isn't on groundwork-languages's path (it needs no Ring), and decodes values as form text by default.

**The price:** a value with a space or a character outside RFC 6265's is now no cookie; no language tag has one. groundwork-access reads its session cookie by the same rules, in its own copy: the two libraries share no dependency to keep one in.

**Instead of:**
- **the split**: quotes, spaces and bad pairs each a case nobody wrote down
- **ring-core's `ring.middleware.cookies`**: a dependency for one header
