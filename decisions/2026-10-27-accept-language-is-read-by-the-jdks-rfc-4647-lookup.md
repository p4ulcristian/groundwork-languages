# Accept-Language is read by the JDK's RFC 4647 lookup

> The user's decision, 2026-10-27 (keep groundwork minimal; take a library only where the problem is one people get subtly wrong, formats among them, and its cost is small where it lands; on the server, the JDK first). Since v0.4.4. Changes in part [A request's language: the prefix, the cookie, Accept-Language, the default](2026-10-23-a-requests-language-and-the-defaults-addresses.md): how Accept-Language picks.

**Decision:** Accept-Language is read by the JDK: `Locale$LanguageRange/parse` makes the header's language ranges, most wanted first, and `Locale/lookupTag` picks the language that shows here, RFC 4647's lookup, which is the rule the earlier decision wrote down ("a tag or its first part; quality 0 is not wanted"), made exact:

- each range, most wanted first (of two equally wanted, the first written), then each shorter tag of it: `de-AT-1996`, then `de-AT`, then `de`;
- a language a range of quality 0 names is not wanted, even through a longer one: `de-AT, de;q=0` is no German;
- `*` is no language;
- the tags the IANA registry says are the same language are: `iw` finds `he`, `he` finds `iw`.

Before the JDK reads it, the header is taken as HTTP's list (RFC 9110, 5.6.1): split by `,`, an empty element left out, the spaces and tabs in each taken off. A header the JDK can't read (a range that isn't one, `en_US`; a quality above 1 or not a number; a parameter other than `q`) is no wish at all: the request goes on to the default, as one without the header.

**Why:** the hand-written reading split on `,` and `;`, matched `q=` with a regex, and tried a tag and its first part only. RFC 4647's lookup is the standard rule for exactly this choice, and the JDK has had it since Java 8, already on every server: nothing to add.

**The price:** what reads differently from v0.4.3, each in the tests:

- `de-AT, de;q=0` is the default, where v0.4.3 picked `de` through `de-AT`;
- `pt-BR-x-a` finds a `pt-br` language, where v0.4.3 tried only `pt-br-x-a` and `pt`;
- a header with one bad range (`en_US, de`, `de;q=abc`, `de;q=0.5;level=1`) is read as none, where v0.4.3 left the bad entry out (or read its `q` anyway) and read the rest;
- `Q=0.5` is a quality, as HTTP's grammar is case-insensitive, where v0.4.3 read only `q=`.

**Instead of:**
- **the hand-written reading**: a second, slightly different rule for a thing with a standard one
- **`Locale/filterTags`**: RFC 4647's filtering, where a range finds every longer tag (`de` finds `de-at`); the decision's rule goes the other way, a range finds itself or a shorter tag
- **reading the good ranges of a bad header**: a guess at what a broken client meant; browsers send headers the JDK reads
