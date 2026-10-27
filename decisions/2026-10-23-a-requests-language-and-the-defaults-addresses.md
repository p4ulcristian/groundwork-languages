> **Changed in part** by [Accept-Language is read by the JDK's RFC 4647 lookup](2026-10-27-accept-language-is-read-by-the-jdks-rfc-4647-lookup.md) (2026-10-27, v0.4.4): a range, then each shorter tag of it; a language of quality 0 is not wanted through a longer range either; a header the JDK can't read is none. And by [The language cookie is read by RFC 6265's grammar](2026-10-27-the-language-cookie-is-read-by-rfc-6265s-grammar.md) (2026-10-27, v0.4.4): a quoted value without its quotes, a name twice its first.

> **Changed in part** by [Texts are ICU messages in a flat JSON catalog per language, read by both sides](2026-10-23-texts-are-icu-messages-in-a-json-catalog-per-language.md) (2026-10-23, v0.2.0): the languages read from a prefix, the cookie and Accept-Language are the ones that show: the launched `:languages` and this environment's `:preview-languages`.

# A request's language: the prefix, the cookie, Accept-Language, the default; the default's addresses carry no prefix

> The user's decision, 2026-10-23 (the order); the prefix rule decided in building v0.1.0. As Groundwork v0.5.0 did.

**Decision:**

- **A request's language** is, in order: the address's prefix (`/de/orders`), when the app has more than one language; then the cookie the settings name (`:cookie-name`), when it holds one of the languages; then the browser's `Accept-Language`, most wanted first, a tag or its first part (`de-AT` is `de`; quality 0 is not wanted); then `:default`. With one language every request is in it, and no prefix is read.
- **The default language's addresses carry no prefix**: `/orders` is English while German is `/de/orders`. A prefix of the default given anyway (`/en/orders`) is read as it, so a typed address works; dictionary never makes one.
- **An address without a prefix is in the visit's language**: a German browser opening `/orders` gets `de` (its Accept-Language), and the page says `<html lang="de">`. In the browser, `find-language-of-path` gives the prefix's language, else the language the server picked for the page.
- **The alternate links** name the same page in every language, with the site's origin, and `x-default`, the default's address; none with one language.
- **dictionary reads the cookie; the app writes it**, when a person picks a language.

**Why the default has no prefix:** one address per page for the default language, as the site had before it had a second one; nothing moves when a language is added. Every language prefixed would need the bare address (`/orders`) to answer with a redirect to a picked language's, a second answer for every page, and a home page that never shows itself.

**The price:** `/orders` shows a German visitor German, while the alternate links say `/orders` is the English page: a search engine, which sends no cookie and its own Accept-Language, sees the default there. A person's choice shows in the address only after a move (parlor keeps the language, so the next address is `/de/…`).

**Instead of:**
- every language with a prefix: a redirect for every bare address
- the default's prefix refused (404 for `/en/orders`): a typed or old link fails for no reason a person can see
- Accept-Language before the cookie: a person's own choice would lose to their browser's
