(ns groundwork-languages.catalogs
  "The catalogs on the server: read from the classpath, each language's
   <catalogs-path>/<language>.json, and a message made into text by ICU4J
   in the language bound around the caller, *bound*, by with-language
   (make-bound-text, tr's). make-text, with the language as a value, is
   what make-bound-text calls; it isn't in the api: tr is the one text
   call."
  (:require
   [clojure.java.io :as io]
   [clojure.set :as set]
   [groundwork-languages.settings :as settings]
   [groundwork-languages.texts :as texts]
   [jsonista.core :as json])
  (:import
   (com.ibm.icu.text MessageFormat)
   (com.ibm.icu.util ULocale)
   (java.util HashMap Map)))

(defn list-catalog-files
  "Every language that shows, with its catalog's classpath resource and
   the address the browser fetches it at: [{:language \"de\" :resource
   \"i18n/de.json\" :address \"/i18n/de.json\"} …]."
  [{:keys [catalogs-path] :as settings}]
  (mapv (fn [language]
          {:language language
           :resource (str catalogs-path "/" language ".json")
           :address  (str "/" catalogs-path "/" language ".json")})
        (settings/list-live-languages settings)))

(defn- read-catalog [resource]
  (when-let [url (io/resource resource)]
    (json/read-value (slurp url))))

(defn load-texts
  "Every language that shows, its catalog read from the classpath: the
   texts (groundwork-languages.texts). A catalog that isn't there throws, naming
   each."
  [{:keys [fallback-language] :as settings}]
  (let [files   (list-catalog-files settings)
        read    (into {} (map (fn [{:keys [language resource]}] [language (read-catalog resource)])) files)
        missing (vec (for [{:keys [language resource]} files :when (nil? (get read language))] resource))]
    (when (seq missing)
      (throw (ex-info (str "No catalog on the classpath for " (pr-str missing))
                      {:type :groundwork-languages/missing-catalogs :resources missing})))
    {:fallback-language fallback-language :catalogs read}))

(defn- find-catalog-times
  "Each catalog's file's last change, by resource; 0 for one inside a jar,
   which never changes, and for one that isn't there."
  [settings]
  (into {}
        (map (fn [{:keys [resource]}]
               (let [url (io/resource resource)]
                 [resource (if (and url (= "file" (.getProtocol url)))
                             (.lastModified (io/file url))
                             0)])))
        (list-catalog-files settings)))

(defn make-texts-reader
  "A function of no arguments giving the texts (load-texts's). watch?
   false: read once, here. watch? true, where a place watches its files
   (development): each call looks at the catalogs' files and reads them
   all again when one changed, so an edited catalog is what the next call
   gives; a catalog that can't be read then throws from that call."
  [settings watch?]
  (if-not watch?
    (constantly (load-texts settings))
    (let [read (atom {:times (find-catalog-times settings) :texts (load-texts settings)})]
      (fn []
        (let [times (find-catalog-times settings)]
          (:texts (if (= times (:times @read))
                    @read
                    (reset! read {:times times :texts (load-texts settings)}))))))))

(defn- format-message [^String message ^String language args]
  (.format (MessageFormat. message (ULocale. language)) ^Map (HashMap. ^Map (texts/turn-args-into-names args))))

(defn- make-text
  "The key's message in the language, its ICU arguments filled in from
   args ({:count 3}, {} for none); in the fallback language when the
   language's catalog lacks it; the key's name when neither has it."
  [texts language message-key args]
  (if-let [{:keys [message] found-in :language} (texts/find-message texts language message-key)]
    (format-message message found-in args)
    (texts/turn-key-into-catalog-key message-key)))

(def ^:dynamic *bound*
  "{:texts … :language …}, bound by with-language for everything inside;
   nil outside, where tr throws."
  nil)

(defn make-bound
  "{:texts … :language …} for with-language to bind. Throws
   :groundwork-languages/bad-texts when texts isn't load-texts's
   {:fallback-language … :catalogs …}, and :groundwork-languages/bad-language when
   language isn't a language's tag, a non-empty string (nil among them)."
  [texts language]
  (when-not (and (map? texts) (string? (:fallback-language texts)) (map? (:catalogs texts)))
    (throw (ex-info "with-language takes the texts load-texts made"
                    {:type :groundwork-languages/bad-texts})))
  (when-not (and (string? language) (seq language))
    (throw (ex-info (str "with-language takes a language's tag, not " (pr-str language))
                    {:type :groundwork-languages/bad-language :language language})))
  {:texts texts :language language})

(defn read-bound-language
  "The language bound by with-language; throws :groundwork-languages/no-language
   when none is."
  []
  (or (:language *bound*)
      (throw (ex-info "No language bound: read inside with-language (groundwork binds the request's)"
                      {:type :groundwork-languages/no-language}))))

(defn make-bound-text
  "The key's message in the bound language (make-text's); throws
   :groundwork-languages/no-language, naming the key, when none is bound."
  [message-key args]
  (let [{:keys [texts language]} *bound*]
    (when-not language
      (throw (ex-info (str "No language bound for " (texts/turn-key-into-catalog-key message-key)
                           ": tr is called inside with-language (groundwork binds the request's)")
                      {:type :groundwork-languages/no-language :key message-key})))
    (make-text texts language message-key args)))

(defn- find-bad-messages [language catalog]
  (into (sorted-map)
        (keep (fn [[k message]]
                (try (MessageFormat. ^String message (ULocale. ^String language)) nil
                     (catch Exception e [k (ex-message e)]))))
        catalog))

(defn- find-argument-names
  "The names of the data a reason's shape carries, as its message names
   them: nil for no data, the keys of a [:map …] written as a vector; nil
   for :any-name (unknown) when the shape is anything else."
  [shape]
  (cond (nil? shape) #{}
        (and (vector? shape) (= :map (first shape)))
        (set (for [entry (rest shape) :when (vector? entry)] (texts/turn-key-into-argument-name (first entry))))
        :else :any-name))

(defn- find-problems-of-reason
  "{:missing-texts [reason]}, {:unknown-arguments {reason [names]}} or
   {:bad-messages {reason why}} for one reason in one catalog; nil when
   its message is there and names only what its data carries."
  [language catalog [reason shape]]
  (if-let [message (get catalog (texts/turn-key-into-catalog-key reason))]
    (let [known (find-argument-names shape)]
      (try (let [named   (set (.getArgumentNames (MessageFormat. ^String message (ULocale. ^String language))))
                 unknown (when (set? known) (sort (remove known named)))]
             (when (seq unknown) {:unknown-arguments {reason (vec unknown)}}))
           (catch Exception e {:bad-messages {reason (ex-message e)}})))
    {:missing-texts [reason]}))

(defn find-reasons-without-texts
  "{} when every reason has its text in every catalog of texts
   (load-texts's: every language that shows), and each text names only
   arguments its reason's data carries; else by language :missing-texts,
   :unknown-arguments and :bad-messages."
  [{:keys [catalogs]} reasons]
  (into {}
        (keep (fn [[language catalog]]
                (let [problems (apply merge-with into {} (keep #(find-problems-of-reason language catalog %) (sort-by (comp str key) reasons)))]
                  (when (seq problems) [language problems]))))
        catalogs))

(defn check-texts-of-reasons
  "Returns reasons; throws :groundwork-languages/missing-texts with every problem at
   once (find-reasons-without-texts's) when one lacks a text."
  [texts reasons]
  (let [problems (find-reasons-without-texts texts reasons)]
    (when (seq problems)
      (throw (ex-info (str "Some reasons have no text in every language: " (pr-str problems))
                      {:type :groundwork-languages/missing-texts :problems problems})))
    reasons))

(defn find-problems-in-catalogs
  "For an app's test, so catalogs stay complete before they ship: every
   key in every language. {} when there are none; else by language, the
   keys some other catalog has and this one lacks (:missing-keys) and the
   messages ICU can't read (:bad-messages, with why); a catalog that isn't
   there, under :missing-catalogs."
  [settings]
  (let [files    (list-catalog-files settings)
        read     (into {} (keep (fn [{:keys [language resource]}] (some->> (read-catalog resource) (vector language)))) files)
        all-keys (into #{} (mapcat keys) (vals read))
        missing  (vec (for [{:keys [language resource]} files :when (not (contains? read language))] resource))]
    (cond-> (into {}
                  (keep (fn [[language catalog]]
                          (let [lacks (sort (set/difference all-keys (set (keys catalog))))
                                bad   (find-bad-messages language catalog)]
                            (when (or (seq lacks) (seq bad))
                              [language (cond-> {}
                                          (seq lacks) (assoc :missing-keys (vec lacks))
                                          (seq bad)   (assoc :bad-messages bad))]))))
                  read)
      (seq missing) (assoc :missing-catalogs missing))))
