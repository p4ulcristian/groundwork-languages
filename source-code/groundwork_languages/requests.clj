(ns groundwork-languages.requests
  "A request's language, on the server: the address's prefix (with more
   than one language), then the cookie (when the settings name one and it
   holds one of the languages), then the browser's Accept-Language (a tag,
   or a shorter one of it: de-AT is de; RFC 4647's lookup, the JDK's), then
   the default. With one language, every request is in it."
  (:require
   [clojure.string :as str]
   [groundwork-languages.addresses :as addresses]
   [groundwork-languages.settings :as settings])
  (:import
   (java.util Locale Locale$LanguageRange)))

;; RFC 6265, 4.2.1: cookie-string = cookie-pair *( ";" SP cookie-pair ),
;; cookie-pair = cookie-name "=" cookie-value, the name a token (RFC 9110's
;; tchar), the value cookie-octets, or cookie-octets in double quotes
(def ^:private cookie-name-pattern #"[!#$%&'*+.^_`|~0-9A-Za-z-]+")
(def ^:private cookie-octets-pattern #"[\x21\x23-\x2B\x2D-\x3A\x3C-\x5B\x5D-\x7E]*")

(defn- read-cookie-pair
  "[name value] of one cookie-pair, its double quotes taken off; nil for a
   pair that isn't one: no =, a name that isn't a token, a value with a
   space, a comma, a backslash or a character outside ASCII in it."
  [pair]
  (when-let [at (str/index-of pair "=")]
    (let [cookie-name (subs pair 0 at)
          value       (subs pair (inc at))
          value       (if (and (<= 2 (count value)) (str/starts-with? value "\"") (str/ends-with? value "\""))
                        (subs value 1 (dec (count value)))
                        value)]
      (when (and (re-matches cookie-name-pattern cookie-name) (re-matches cookie-octets-pattern value))
        [cookie-name value]))))

(defn read-cookie
  "The value of the cookie named cookie-name in the request's Cookie
   header, by RFC 6265's grammar: pairs split by ;, the spaces and tabs
   around each taken off, a quoted value without its quotes, a pair that
   isn't one left out; the first of that name with a value (the browser
   lists the one for the longest path first), an empty one none; or nil."
  [request cookie-name]
  (some (fn [pair]
          (when-let [[k v] (read-cookie-pair (str/trim pair))]
            (when (= k cookie-name) (not-empty v))))
        (str/split (or (get-in request [:headers "cookie"]) "") #";")))

(defn read-accepted-languages
  "Accept-Language as the JDK's language ranges (RFC 4647), most wanted
   first, by Locale$LanguageRange/parse: HTTP's list split by , (an empty
   element allowed, RFC 9110 5.6.1), the spaces and tabs in each taken
   off. nil for no header, an empty one, or one the JDK can't read (a range
   that isn't one, q above 1 or not a number, a parameter other than q):
   no wish read at all."
  [header]
  (let [elements (remove str/blank? (map #(str/replace % #"[ \t]" "") (str/split (or header "") #",")))]
    (when (seq elements)
      (try
        (Locale$LanguageRange/parse (str/join "," elements))
        (catch IllegalArgumentException _ nil)))))

(defn- find-accepted-language
  "The language that shows here Accept-Language wants most, by RFC 4647's
   lookup (Locale/lookupTag): each range, most wanted first, then each
   shorter of it (de-AT-1996, de-AT, de); a range of quality 0 is not
   wanted, and * is no language; nil when none fits."
  [settings header]
  (when-let [ranges (read-accepted-languages header)]
    (Locale/lookupTag ranges (settings/list-live-languages settings))))

(defn find-language-of-request
  "{:language \"de\" :path \"/orders\"}: the request's language, and its
   path (:uri) without the language's prefix."
  [{:keys [default cookie-name] :as settings} request]
  (let [{:keys [language path]} (addresses/split-language-from-path settings (:uri request))
        live                    (settings/list-live-languages settings)]
    {:language (or language
                   (when (= 1 (count live)) default)
                   (when cookie-name ((set live) (read-cookie request cookie-name)))
                   (find-accepted-language settings (get-in request [:headers "accept-language"]))
                   default)
     :path     path}))

(defn make-alternate-links
  "The same page in every language, for <link rel=\"alternate\"
   hreflang>: [{:hreflang \"en\" :href \"https://shop.example/orders\"}
   {:hreflang \"de\" :href \"https://shop.example/de/orders\"} …
   {:hreflang \"x-default\" :href the default's}]; none with one
   language. path carries no language; origin is the site's,
   \"https://shop.example\"."
  [{:keys [default] :as settings} origin path]
  (if (< 1 (count (settings/list-live-languages settings)))
    (conj (mapv (fn [language] {:hreflang language :href (str origin (addresses/make-path-in-language settings path language))})
                (settings/list-live-languages settings))
          {:hreflang "x-default" :href (str origin (addresses/make-path-in-language settings path default))})
    []))
