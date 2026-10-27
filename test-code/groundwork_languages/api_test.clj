(ns groundwork-languages.api-test
  "The server's side: a request's language, by its prefix, the cookie,
   Accept-Language and the default; the alternate links; the settings
   checked."
  (:require
   [clojure.test :refer [deftest is testing]]
   [groundwork-languages.api :as languages]))

(def ^:private settings
  (languages/check-settings {:languages [{:code "en" :name "English"} {:code "de" :name "Deutsch"}] :default "en" :cookie-name "lang"
                              :preview-languages [{:code "hu" :name "Magyar"}] :fallback-language "en" :catalogs-path "groundwork_languages_test/i18n"}))

(defn- make-request [uri headers]
  {:uri uri :headers headers})

(deftest the-names-of-the-api
  (is (= '#{settings-shape check-settings find-language-of-request make-alternate-links
            split-language-from-path make-path-in-language list-live-languages list-languages-to-pick
            list-catalog-files load-texts with-language tr read-bound-language find-problems-in-catalogs
            check-texts-of-reasons}
         (set (keys (ns-publics 'groundwork-languages.api))))))

(deftest a-requests-language
  (testing "the address's prefix first, taken off the path"
    (is (= {:language "de" :path "/orders/7"}
           (languages/find-language-of-request settings (make-request "/de/orders/7" {"cookie" "lang=hu" "accept-language" "hu"}))))
    (is (= {:language "de" :path "/"} (languages/find-language-of-request settings (make-request "/de" {}))))
    (is (= {:language "en" :path "/orders"} (languages/find-language-of-request settings (make-request "/en/orders" {})))
        "the default's prefix, given anyway, is read as it"))
  (testing "then the cookie the settings name, when it holds one of the languages"
    (is (= "hu" (:language (languages/find-language-of-request settings (make-request "/orders" {"cookie" "a=1; lang=hu" "accept-language" "de"})))))
    (is (= "de" (:language (languages/find-language-of-request settings (make-request "/orders" {"cookie" "lang=fr" "accept-language" "de"}))))))
  (testing "then Accept-Language, most wanted first; a region falls back to its language"
    (is (= {:language "de" :path "/orders"}
           (languages/find-language-of-request settings (make-request "/orders" {"accept-language" "fr;q=0.9, de-AT;q=0.8, en;q=0.1"}))))
    (is (= "en" (:language (languages/find-language-of-request settings (make-request "/orders" {"accept-language" "de;q=0, fr"}))))
        "quality 0 is not wanted"))
  (testing "then the default"
    (is (= "en" (:language (languages/find-language-of-request settings (make-request "/orders" {}))))))
  (testing "Accept-Language by RFC 4647's lookup, the JDK's"
    (let [language-of #(:language (languages/find-language-of-request settings (make-request "/orders" {"accept-language" %})))]
      (is (= "de" (language-of "de-AT")) "a region: its language")
      (is (= "de" (language-of "DE-at")) "in any case")
      (is (= "de" (language-of "de-AT-1996")) "each shorter tag in turn")
      (is (= "en" (language-of "de-AT, de;q=0")) "a region of a language of quality 0: not wanted")
      (is (= "en" (language-of "*")) "* is no language: the default")
      (is (= "de" (language-of "*;q=0.5, de;q=0.1")) "* aside, the next")
      (is (= "hu" (language-of "fr;q=0.9,hu;q=0.8 ,  de;q=0.8")) "spaces; of two equally wanted, the first")
      (is (= "de" (language-of "fr,\tde ; Q=0.5")) "a tab, spaces around ;, Q in capitals")
      (is (= "de" (language-of "fr,,de")) "an empty element of the list")
      (is (= "en" (language-of "de;q=abc")) "a quality that isn't a number: the header isn't read")
      (is (= "en" (language-of "de;q=1.5")) "a quality above 1: the header isn't read")
      (is (= "en" (language-of "en_US, de")) "a range that isn't one: the header isn't read")
      (is (= "en" (language-of "de;q=0.5;level=1")) "a parameter other than q: the header isn't read")
      (is (= "en" (language-of "")) "an empty header")))
  (testing "the cookie by RFC 6265's grammar"
    (let [language-of #(:language (languages/find-language-of-request settings (make-request "/orders" {"cookie" % "accept-language" "de"})))]
      (is (= "hu" (language-of "lang=\"hu\"")) "a quoted value, without its quotes")
      (is (= "hu" (language-of "lang=hu; lang=en")) "a name twice: the first")
      (is (= "hu" (language-of "lang=; lang=hu")) "an empty one is none")
      (is (= "hu" (language-of "a=b c; =x; nameless; \tlang=hu ")) "bad pairs left out, spaces and tabs around a pair")
      (is (= "de" (language-of "lang=h u")) "a space in a value: no cookie, Accept-Language")
      (is (= "de" (language-of "my_lang=hu; lang_old=hu")) "the name exactly")))
  (testing "a first part that isn't a language stays in the path"
    (is (= {:language "en" :path "/deals"} (languages/find-language-of-request settings (make-request "/deals" {})))))
  (testing "no cookie named: it isn't read"
    (is (= "en" (:language (languages/find-language-of-request (assoc settings :cookie-name nil)
                                                                (make-request "/orders" {"cookie" "lang=hu"}))))))
  (testing "one language: every request is in it, and no prefix is taken"
    (let [one {:languages [{:code "hu" :name "Magyar"}] :default "hu" :cookie-name nil :preview-languages [] :fallback-language "hu" :catalogs-path "i18n"}]
      (is (= {:language "hu" :path "/de/orders"}
             (languages/find-language-of-request one (make-request "/de/orders" {"accept-language" "de"})))))))

(deftest alternate-links
  (is (= [{:hreflang "en" :href "https://shop.example/orders"}
          {:hreflang "de" :href "https://shop.example/de/orders"}
          {:hreflang "hu" :href "https://shop.example/hu/orders"}
          {:hreflang "x-default" :href "https://shop.example/orders"}]
         (languages/make-alternate-links settings "https://shop.example" "/orders")))
  (is (= ["https://shop.example/" "https://shop.example/de" "https://shop.example/hu" "https://shop.example/"]
         (map :href (languages/make-alternate-links settings "https://shop.example" "/")))
      "the home page")
  (is (= [] (languages/make-alternate-links {:languages [{:code "hu" :name "Magyar"}] :default "hu" :cookie-name nil :preview-languages []
                                              :fallback-language "hu" :catalogs-path "i18n"} "https://x" "/"))
      "none with one language"))

(deftest the-settings-are-checked
  (let [errors (try (languages/check-settings {:languages ["en" "De"] :colour "red"})
                    (catch clojure.lang.ExceptionInfo e (:errors (ex-data e))))]
    (is (= #{:languages :default :cookie-name :preview-languages :fallback-language :catalogs-path :colour} (set (keys errors)))
        "every problem at once"))
  (let [complete {:languages [{:code "en" :name "English"} {:code "de" :name "Deutsch"}] :default "en" :cookie-name nil :preview-languages [{:code "hu" :name "Magyar"}]
                  :fallback-language "en" :catalogs-path "i18n"}]
    (is (= complete (languages/check-settings complete)))
    (is (thrown? clojure.lang.ExceptionInfo (languages/check-settings (assoc complete :default "hu")))
        "the default is a launched language")
    (is (thrown? clojure.lang.ExceptionInfo (languages/check-settings (assoc complete :fallback-language "fr")))
        "the fallback is a language that shows")
    (is (thrown? clojure.lang.ExceptionInfo (languages/check-settings (assoc complete :preview-languages [{:code "de" :name "Deutsch"}])))
        "each language once")
    (is (thrown? clojure.lang.ExceptionInfo (languages/check-settings (assoc complete :catalogs-path "/i18n/"))))
    (is (= {:languages [nil {:name ["missing required key"]}]}
           (try (languages/check-settings (assoc complete :languages [{:code "en" :name "English"} {:code "de"}]))
                (catch clojure.lang.ExceptionInfo e (select-keys (:errors (ex-data e)) [:languages]))))
        "a language without its name")
    (is (thrown? clojure.lang.ExceptionInfo (languages/check-settings (assoc complete :languages [{:code "en" :name "English" :flag "🇺🇸"}])))
        "no flag: the menu's look is the app's")))

(deftest the-languages-a-menu-lists
  (is (= [{:code "en" :name "English"} {:code "de" :name "Deutsch"} {:code "hu" :name "Magyar"}]
         (languages/list-languages-to-pick settings))
      "the launched ones, then the preview ones that show here, in the settings' order")
  (is (= ["en" "de" "hu"] (languages/list-live-languages settings)) "the tags")
  (is (= [{:code "en" :name "English"} {:code "de" :name "Deutsch"}]
         (languages/list-languages-to-pick (assoc settings :preview-languages [])))
      "a preview language that doesn't show here isn't listed"))

(deftest preview-languages-show-only-where-the-settings-say
  (is (= "hu" (:language (languages/find-language-of-request settings (make-request "/hu/orders" {}))))
      "shown here: its prefix is read")
  (let [production (assoc settings :preview-languages [] :catalogs-path "groundwork_languages_test/i18n")]
    (is (= {:language "en" :path "/hu/orders"} (languages/find-language-of-request production (make-request "/hu/orders" {"accept-language" "hu"})))
        "not shown: neither its prefix nor Accept-Language")
    (is (= ["en" "de"] (map :hreflang (butlast (languages/make-alternate-links production "https://x" "/")))))))

(deftest texts-in-a-language
  (let [texts (languages/load-texts settings)]
    (is (= ["en" "de" "hu"] (map :language (languages/list-catalog-files settings))))
    (is (= "/groundwork_languages_test/i18n/de.json" (:address (second (languages/list-catalog-files settings)))))
    (is (= "Add 3 items" (languages/with-language texts "en" (languages/tr :orders.cart/add {:count 3}))))
    (is (= "1 Artikel hinzufügen" (languages/with-language texts "de" (languages/tr :orders.cart/add {:count 1}))) "the language's plural rules")
    (is (= "Cart" (languages/with-language texts "de" (languages/tr :orders.cart/title {}))) "missing in German: the fallback's")
    (is (= "orders.cart/nowhere" (languages/with-language texts "de" (languages/tr :orders.cart/nowhere {}))) "missing everywhere: the key's name")
    (is (= "Klein (<30cm)" (languages/with-language texts "de" (languages/tr :sizes/small {}))) "a < is text")
    (is (= "Kosár" (languages/with-language texts "hu" (languages/tr :orders.cart/title {}))))))

(deftest a-catalog-that-isnt-there-is-named
  (is (= {:type :groundwork-languages/missing-catalogs :resources ["groundwork_languages_test/i18n/fr.json"]}
         (try (languages/load-texts (assoc settings :preview-languages [{:code "hu" :name "Magyar"} {:code "fr" :name "Français"}]))
              (catch clojure.lang.ExceptionInfo e (ex-data e))))))

(deftest the-catalogs-are-kept-complete
  (is (= {} (languages/find-problems-in-catalogs (assoc settings :languages [{:code "en" :name "English"} {:code "hu" :name "Magyar"}] :preview-languages []))))
  (let [problems (languages/find-problems-in-catalogs {:languages [{:code "en" :name "English"} {:code "de" :name "Deutsch"}] :default "en" :cookie-name nil :preview-languages [{:code "fr" :name "Français"}]
                                                        :fallback-language "en" :catalogs-path "groundwork_languages_test/gaps"})]
    (is (= ["a/three"] (get-in problems ["en" :missing-keys])))
    (is (= ["a/two"] (get-in problems ["de" :missing-keys])) "every key in every language")
    (is (contains? (get-in problems ["en" :bad-messages]) "a/two") "ICU can't read it")
    (is (= ["groundwork_languages_test/gaps/fr.json"] (:missing-catalogs problems)))))

(deftest tr-in-the-bound-language
  (let [texts (languages/load-texts settings)]
    (is (= "3 Artikel hinzufügen" (languages/with-language texts "de" (languages/tr :orders.cart/add {:count 3}))))
    (is (= "Cart" (languages/with-language texts "de" (languages/tr :orders.cart/title {}))) "the fallback's")
    (is (= ["Add 1 item" "1 Artikel hinzufügen"]
           (languages/with-language texts "en"
             [(languages/tr :orders.cart/add {:count 1})
              (languages/with-language texts "de" (languages/tr :orders.cart/add {:count 1}))]))
        "the innermost binding")
    (is (= "Kosár" (languages/with-language texts "hu" @(future (languages/tr :orders.cart/title {}))))
        "a future carries it")
    (is (= {:type :groundwork-languages/no-language :key :orders.cart/add}
           (try (languages/tr :orders.cart/add {:count 3})
                (catch clojure.lang.ExceptionInfo e (ex-data e))))
        "none bound: an error, not a language nobody chose")
    (is (= {:type :groundwork-languages/bad-language :language nil}
           (try (languages/with-language texts nil (languages/tr :orders.cart/add {:count 3}))
                (catch clojure.lang.ExceptionInfo e (ex-data e))))
        "a nil language is refused where it is bound, not later by tr")
    (is (= :groundwork-languages/bad-texts
           (try (languages/with-language nil "de" (languages/tr :orders.cart/add {:count 3}))
                (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))
    (is (= "de" (languages/with-language texts "de" (languages/read-bound-language))))
    (is (= :groundwork-languages/no-language (try (languages/read-bound-language) (catch clojure.lang.ExceptionInfo e (:type (ex-data e))))))))

(def ^:private reason-texts
  {:fallback-language "en"
   :catalogs          {"en" {"order/already-shipped" "Shipped on {shipped_at}."
                             "order/no-address"      "This order has no address."
                             "groundwork-bridge/too-many"       "Too many: try again in {retry_after_ms, number} ms."}
                       "de" {"order/already-shipped" "Am {shipped_at} verschickt."
                             "groundwork-bridge/too-many"       "Zu viele, {seconds}."}}})

(def ^:private reasons
  {:order/already-shipped [:map [:order/shipped-at :time/instant]]
   :order/no-address      nil
   :groundwork-bridge/too-many       [:map {:closed true} [:retry-after-ms :int]]})

(deftest every-reason-has-a-text-in-every-language
  (is (= reasons (languages/check-texts-of-reasons (update reason-texts :catalogs dissoc "de") reasons))
      "every reason in every catalog, each naming only what its data carries")
  (let [{:keys [type problems]} (try (languages/check-texts-of-reasons reason-texts reasons)
                                     (catch clojure.lang.ExceptionInfo e (ex-data e)))]
    (is (= :groundwork-languages/missing-texts type))
    (is (= {"de" {:missing-texts [:order/no-address] :unknown-arguments {:groundwork-bridge/too-many ["seconds"]}}} problems)
        "German lacks one, and names an argument the reason's data doesn't carry; the fallback doesn't count"))
  (testing "a reason of no data whose text names one; a text ICU can't read"
    (is (= {"en" {:unknown-arguments {:order/no-address ["street"]} :bad-messages {:order/already-shipped "x"}}}
           (-> (try (languages/check-texts-of-reasons {:fallback-language "en"
                                                        :catalogs {"en" {"order/no-address" "No {street}."
                                                                         "order/already-shipped" "Shipped {broken"
                                                                         "groundwork-bridge/too-many" "Later."}}}
                                                       reasons)
                    (catch clojure.lang.ExceptionInfo e (ex-data e)))
               :problems
               (update-in ["en" :bad-messages] update-vals (constantly "x")))))))

(deftest a-reasons-data-are-its-texts-arguments
  (testing "the key's name without its namespace, - read as _: ICU's names take no -"
    (is (= "Too many: try again in 1,500 ms."
           (languages/with-language reason-texts "en" (languages/tr :groundwork-bridge/too-many {:retry-after-ms 1500}))))
    (is (= "Shipped on today." (languages/with-language reason-texts "en" (languages/tr :order/already-shipped {:order/shipped-at "today"}))))))
