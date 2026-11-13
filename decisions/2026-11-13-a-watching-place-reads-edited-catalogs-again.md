# A watching place reads edited catalogs again

> The user's decision, 2026-11-13, from Iron Rainbow's customizer work: every new text needed a restart of the development server.

**Decision:** `make-texts-reader settings watch?` gives the texts through a function of no arguments. With `watch?` false the catalogs are read once. With `watch?` true (a place that watches its files: development) each call looks at the catalogs' files and reads them all again when one changed. The composer passes its own watching (its `:browser-build` watches) and serves the browser's catalog files the same way.

```clojure
(def texts-now (languages/make-texts-reader (:languages settings) watching?))
(languages/with-language (texts-now) "de" (languages/tr :orders.cart/add {:count 3}))
```

**Why:** editing en.json and reloading the page should show the new text; a two-minute restart per text broke the work.

**The price:** in development the start's checks (every reason has its text) run only at the start, not on an edited catalog; a catalog saved half-written throws from the next request until it is fixed. A page still has to be reloaded.

**Instead of:** a separate setting for reading once or on every fetch: the place already says whether it watches its files, a second switch could disagree with it. Or re-reading always, prod too: a file looked at on every request where files never change.
