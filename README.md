# groundwork-languages

Which language a request, a page and an address are in, for a Clojure app
on both sides. On the server it picks a request's language (the address's
prefix, `/de/orders`; a cookie; the browser's `Accept-Language`; the
default) and gives the page's alternate links; in the browser it reads the
language from an address and makes an address in a language. It lists
the languages a menu offers, each in its own name ("Deutsch"). And the
texts: ICU MessageFormat messages in a flat JSON catalog per language, the
same file read by the server (ICU4J: emails, PDFs) and fetched by the
browser (intl-messageformat, in the app's package.json); a key missing
from a language falls back to the fallback language, and missing there
shows as its name.

```clojure
io.github.p4ulcristian/groundwork-languages {:git/tag "v0.4.4" :git/sha "68368a0"}
```

Needs malli, jsonista and ICU4J; in the browser, intl-messageformat. One public namespace, `groundwork-languages.api`: a `.clj` for the
server, a `.cljs` for the browser.

## The api

| side | name | does |
|---|---|---|
| both | `settings-shape` `check-settings` | the settings, every key required, checked once before use |
| server | `find-language-of-request` | `{:language :path}`: the request's language, and its path without the prefix |
| server | `make-alternate-links` | the page in every language, and `x-default`: `[{:hreflang :href} …]` |
| browser | `find-language-of-path` | the path's prefix's language, else the page's |
| both | `split-language-from-path` `make-path-in-language` | `/de/orders` ⇄ `{:language "de" :path "/orders"}` |
| both | `list-live-languages` | the tags of the launched languages, then of the preview ones that show here |
| both | `list-languages-to-pick` | the same languages, `{:code :name}` each, in the settings' order: what a language menu lists |
| server | `load-texts` | every catalog that shows, read once |
| server | `with-language` `tr` `read-bound-language` | texts and a language bound around a body, both checked first (`:groundwork-languages/bad-texts`, `:groundwork-languages/bad-language`); `(tr :key args)` in it, the one text call on the server ([why](decisions/2026-10-23-tr-is-the-one-text-call-on-the-server.md)), and the language itself (`<html lang>`); an error where none is bound |
| server | `list-catalog-files` | each catalog's classpath resource and the address the browser fetches it at |
| server | `find-problems-in-catalogs` | for a test: every key in every language, every message ICU reads |
| server | `check-texts-of-reasons` | for the start: every reason a refusal may give (bridge's named refusals: the handlers' and the libraries'), `{reason data-shape}`, has its text in every catalog, naming only arguments its data carries; else `:groundwork-languages/missing-texts`, every problem at once ([why](https://github.com/p4ulcristian/groundwork-bridge/blob/tidy-up/decisions/2026-10-23-a-refusal-is-a-named-reason-declared-on-its-handler.md)) |
| browser | `fetch-catalog!` `make-text` | a language's catalog from the server; a message in a language, for groundwork-screens's `tr` (the app calls `screens/tr`) |

```clojure
(require '[groundwork-languages.api :as languages])

(def settings (languages/check-settings {:languages         [{:code "en" :name "English"}   ; launched, each in its own name
                                                              {:code "de" :name "Deutsch"}]
                                          :default           "en"     ; its addresses carry no prefix
                                          :cookie-name       "lang"   ; or nil: no cookie read (the app writes it)
                                          :preview-languages []       ; not launched, shown here: [{:code "hu" :name "Magyar"}] on a stage
                                          :fallback-language "en"     ; a message missing from a catalog
                                          :catalogs-path     "i18n"})) ; i18n/<language>.json

(languages/find-language-of-request settings {:uri "/de/orders" :headers {}})
;=> {:language "de" :path "/orders"}
(languages/find-language-of-request settings {:uri "/orders" :headers {"accept-language" "de-AT,de;q=0.9"}})
;=> {:language "de" :path "/orders"}
(languages/list-languages-to-pick settings)
;=> [{:code "en" :name "English"} {:code "de" :name "Deutsch"}]   ; a language menu's; flags are the app's
(languages/make-alternate-links settings "https://shop.example" "/orders")
;=> [{:hreflang "en" :href "https://shop.example/orders"}
;    {:hreflang "de" :href "https://shop.example/de/orders"}
;    {:hreflang "x-default" :href "https://shop.example/orders"}]

;; texts, on the server: tr, in the language bound around it
(def texts (languages/load-texts settings))
(languages/with-language texts "de"                           ; groundwork binds the request's
  (languages/tr :orders.cart/add {:count 3}))                  ;=> "3 Artikel hinzufügen"
(languages/with-language texts (:person/language person)      ; a job's email: the person's language, read first
  (languages/tr :emails.order/subject {:order 7}))
(languages/tr :orders.cart/add {:count 3})                     ; none bound: throws :groundwork-languages/no-language
(is (= {} (languages/find-problems-in-catalogs settings)))      ; in the app's tests
;; at the start: each refusal's reason is a text's key, its data the text's arguments,
;; named without their namespace, a - read as _ (ICU's names take no -)
(languages/check-texts-of-reasons texts {:groundwork-bridge/too-many       [:map [:retry-after-ms :int]]   ; "Try again in {retry_after_ms} ms."
                                          :order/already-shipped [:map [:order/shipped-at :time/instant]]
                                          :order/no-address      nil})                            ; a text with no arguments

;; in the browser
(languages/find-language-of-path settings "/orders" "de")   ;=> "de", the page's
(languages/make-path-in-language settings "/orders" "de")   ;=> "/de/orders"
```

Accept-Language is read by the JDK, RFC 4647's lookup: each range, most
wanted first, then each shorter tag of it (`de-AT` finds `de`); a language
of quality 0 is not wanted, `*` is none, and a header the JDK can't read is
no wish at all ([why](decisions/2026-10-27-accept-language-is-read-by-the-jdks-rfc-4647-lookup.md)).
The cookie is read by RFC 6265's grammar: a quoted value without its quotes,
a bad pair left out, a name twice its first with a value
([why](decisions/2026-10-27-the-language-cookie-is-read-by-rfc-6265s-grammar.md)).

## Tests

```sh
clojure -X:test                                # the server's side
npm install && npx shadow-cljs compile test    # the browser's, on node
```

Why it is the way it is: [`decisions/`](decisions/README.md).

MIT license.
