# dictionary decisions

One file per decision: `YYYY-MM-DD-what-we-decided.md`. The title is the decision. Each file says what we decided, why, and what we chose against. A decision changes by a new file; the old one says so at the top (`Replaced by` or `Changed in part by`), and nothing else in it is rewritten.

dictionary is groundwork's languages (groundwork: the libraries and the composer of the same name that puts them together). It carries over the languages of Groundwork v0.5.0, now the groundwork composer (its decisions are in its `decisions/`), and the texts of Iron Rainbow's i18n.

## In force

- [dictionary picks the language; texts come later](2026-10-23-dictionary-picks-the-language-texts-come-later.md), changed in part by [Texts are ICU messages in a flat JSON catalog per language](2026-10-23-texts-are-icu-messages-in-a-json-catalog-per-language.md) and [Each language carries its own name; flags are the app's](2026-10-23-each-language-carries-its-own-name.md)
- [A request's language: the prefix, the cookie, Accept-Language, the default; the default's addresses carry no prefix](2026-10-23-a-requests-language-and-the-defaults-addresses.md), changed in part by [Texts are ICU messages in a flat JSON catalog per language](2026-10-23-texts-are-icu-messages-in-a-json-catalog-per-language.md), [Accept-Language is read by the JDK's RFC 4647 lookup](2026-10-27-accept-language-is-read-by-the-jdks-rfc-4647-lookup.md) and [The language cookie is read by RFC 6265's grammar](2026-10-27-the-language-cookie-is-read-by-rfc-6265s-grammar.md)
- [Texts are ICU messages in a flat JSON catalog per language, read by both sides](2026-10-23-texts-are-icu-messages-in-a-json-catalog-per-language.md), changed in part by [Each language carries its own name; flags are the app's](2026-10-23-each-language-carries-its-own-name.md), [Texts are `(tr key args)`, in the language bound around them; none bound is an error](2026-10-23-texts-are-tr-under-a-bound-language.md) and [tr is the one text call on the server](2026-10-23-tr-is-the-one-text-call-on-the-server.md); changed in part also by [A refusal is a named reason](https://github.com/p4ulcristian/bridge/blob/tidy-up/decisions/2026-10-23-a-refusal-is-a-named-reason-declared-on-its-handler.md) (bridge's)
- [Each language carries its own name; flags are the app's](2026-10-23-each-language-carries-its-own-name.md)
- [Texts are `(tr key args)`, in the language bound around them; none bound is an error](2026-10-23-texts-are-tr-under-a-bound-language.md), changed in part by [tr is the one text call on the server](2026-10-23-tr-is-the-one-text-call-on-the-server.md)
- [tr is the one text call on the server](2026-10-23-tr-is-the-one-text-call-on-the-server.md) (built on tidy-up, not released)
- [Accept-Language is read by the JDK's RFC 4647 lookup](2026-10-27-accept-language-is-read-by-the-jdks-rfc-4647-lookup.md) (v0.4.4)
- [The language cookie is read by RFC 6265's grammar](2026-10-27-the-language-cookie-is-read-by-rfc-6265s-grammar.md) (v0.4.4)
