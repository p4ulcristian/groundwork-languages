(ns groundwork-languages.api
  "The request's language, and the page's addresses in every language: the
   server's side. The browser's is this namespace's .cljs half.

     (def settings (languages/check-settings {:languages         [{:code \"en\" :name \"English\"}   ; launched
                                                                {:code \"de\" :name \"Deutsch\"}]
                                               :default           \"en\"     ; its addresses carry no prefix
                                               :cookie-name       \"lang\"   ; or nil
                                               :preview-languages []       ; those that show here too: [{:code \"hu\" :name \"Magyar\"}] on a stage
                                               :fallback-language \"en\"     ; a message missing from a catalog
                                               :catalogs-path     \"i18n\"})) ; i18n/<language>.json, on the classpath

     (languages/find-language-of-request settings request)
     ;=> {:language \"de\" :path \"/orders\"}   ; for /de/orders, or /orders with a German browser
     (languages/make-alternate-links settings \"https://shop.example\" \"/orders\")
     ;=> [{:hreflang \"en\" :href \"https://shop.example/orders\"}
     ;    {:hreflang \"de\" :href \"https://shop.example/de/orders\"}
     ;    {:hreflang \"x-default\" :href \"https://shop.example/orders\"}]
     (languages/make-path-in-language settings \"/orders\" \"de\")   ;=> \"/de/orders\"
     (languages/list-languages-to-pick settings)
     ;=> [{:code \"en\" :name \"English\"} {:code \"de\" :name \"Deutsch\"}]   ; a language menu's

     ;; the texts: ICU MessageFormat messages, a flat JSON catalog per language
     (def texts (languages/load-texts settings))   ; once, at the start
     (languages/with-language texts \"de\"   ; bound: groundwork binds the request's; an email its row's
       (languages/tr :orders.cart/add {:count 3}))   ;=> \"3 Artikel hinzufügen\"

   tr is the one text call: the language is never passed by hand. Code
   outside a request (a job writing an email) binds the language itself,
   with-language, the person's language read first.

   A request's language: the address's prefix (with more than one
   language), then the cookie the settings name, then Accept-Language, then
   the default. A message in a language: its catalog's, else the fallback
   language's, else the key's name, ugly on purpose. The settings are
   checked once, by check-settings, before the app starts; the functions
   trust them."
  (:require
   [groundwork-languages.addresses :as addresses]
   [groundwork-languages.catalogs :as catalogs]
   [groundwork-languages.requests :as requests]
   [groundwork-languages.settings :as settings]))

(def settings-shape
  "The settings, every key required: {:languages [{:code \"en\" :name
   \"English\"} {:code \"de\" :name \"Deutsch\"}] :default \"en\"
   :cookie-name \"lang\" …} (see groundwork-languages.settings)."
  settings/settings-shape)

(defn check-settings
  "Returns the settings; throws {:type :groundwork-languages/bad-settings :errors …}
   with every problem at once when they don't fit."
  [settings]
  (settings/check-settings settings))

(defn find-language-of-request
  "{:language … :path …}: the request's language, and its path (:uri)
   without the language's prefix, for routing."
  [settings request]
  (requests/find-language-of-request settings request))

(defn make-alternate-links
  "The page at path (no language in it) in every language, and x-default,
   the default's, each {:hreflang :href} with the origin in front; [] with
   one language."
  [settings origin path]
  (requests/make-alternate-links settings origin path))

(defn split-language-from-path
  "{:language \"de\" :path \"/orders\"} for /de/orders; {:language nil
   :path path} when the path starts with none (always, with one language)."
  [settings path]
  (addresses/split-language-from-path settings path))

(defn make-path-in-language
  "A path (no language in it) in a language: /de/orders; the default's
   carries none, /orders."
  [settings path language]
  (addresses/make-path-in-language settings path language))

(defn list-live-languages
  "The tags of the languages that show here: the launched :languages,
   then the :preview-languages this environment shows."
  [settings]
  (settings/list-live-languages settings))

(defn list-languages-to-pick
  "What a language menu lists: the languages that show here, in the
   settings' order, each its tag and its name in itself, [{:code \"en\"
   :name \"English\"} {:code \"de\" :name \"Deutsch\"} …]."
  [settings]
  (settings/list-languages-to-pick settings))

;; -----------------------------------------------------------------------------
;; Texts

(defn list-catalog-files
  "Every language that shows, its catalog's classpath resource and the
   address the browser fetches it at: [{:language \"de\" :resource
   \"i18n/de.json\" :address \"/i18n/de.json\"} …], for the app's server
   to serve."
  [settings]
  (catalogs/list-catalog-files settings))

(defn load-texts
  "Every language that shows, its catalog read from the classpath:
   {:fallback-language … :catalogs {language {key message}}}. A catalog
   that isn't there throws, :groundwork-languages/missing-catalogs."
  [settings]
  (catalogs/load-texts settings))

(defn make-texts-reader
  "A function of no arguments giving the texts (load-texts's), for a
   server that hands them to every request. watch? false: the catalogs
   read once, here. watch? true, for a place that watches its files
   (development): each call reads them all again when one of their files
   changed, so an edited catalog shows on the next request without a
   restart; one that can't be read then throws from that call.

     (def texts-now (languages/make-texts-reader settings false))
     (languages/with-language (texts-now) \"de\" ...)"
  [settings watch?]
  (catalogs/make-texts-reader settings watch?))

(defmacro with-language
  "Runs body with texts and the language bound, for every tr inside (and
   on the threads Clojure starts from there: future, pmap, agents; a Java
   executor's or a callback's thread carries nothing). The groundwork
   composer binds the request's around every request; an email, a PDF or
   a job binds the language of the row it is made for. Throws before body
   runs: :groundwork-languages/bad-texts when texts isn't load-texts's,
   :groundwork-languages/bad-language when language isn't a tag (nil, say).

     (languages/with-language texts (:order/language order)
       {:subject (languages/tr :emails.order/subject {:order 7})})"
  [texts language & body]
  `(binding [catalogs/*bound* (catalogs/make-bound ~texts ~language)]
     ~@body))

(defn tr
  "The key's ICU message in the language bound around it (with-language),
   the one text call on the server. args filled in ({} for none; a key
   names its argument without its namespace, a - read as _:
   {:retry-after-ms 3} fills {retry_after_ms}), by ICU4J; the fallback
   language's when the language's catalog lacks it; the key's name,
   \"orders.cart/add\", when neither has it. Throws
   :groundwork-languages/no-language, naming the key, when none is bound: never a
   text in a language nobody chose."
  [message-key args]
  (catalogs/make-bound-text message-key args))

(defn read-bound-language
  "The language bound around the caller (with-language), for what isn't a
   text: <html lang>, the language an order is saved in. Throws
   :groundwork-languages/no-language when none is bound."
  []
  (catalogs/read-bound-language))

(defn check-texts-of-reasons
  "For the start: every reason a refusal may give (bridge's named
   refusals: the handlers' :refusals and the libraries' own, bridge's and
   groundwork-access's), {reason data-shape}, has its text in every catalog of
   texts (load-texts's: every language that shows); the reason is the
   text's key and its data the text's arguments ({retry_after_ms} for
   :retry-after-ms: turn-args-into-names). Throws
   {:type :groundwork-languages/missing-texts :problems {language {…}}} with every
   problem at once: :missing-texts, the reasons a catalog lacks;
   :unknown-arguments, by reason, the arguments a text names that its
   reason's data doesn't carry (checked for a nil shape, which carries
   none, and a [:map …]); :bad-messages, by reason, a text ICU can't read.
   Returns reasons.

     (languages/check-texts-of-reasons texts (merge bridge/refusals access/refusals
                                                     (apply merge (map :refusals (vals handlers)))))"
  [texts reasons]
  (catalogs/check-texts-of-reasons texts reasons))

(defn find-problems-in-catalogs
  "For an app's test: every key in every language, every message ICU can
   read. {} when so; else by language :missing-keys and :bad-messages,
   and :missing-catalogs.

     (is (= {} (languages/find-problems-in-catalogs settings)))"
  [settings]
  (catalogs/find-problems-in-catalogs settings))
