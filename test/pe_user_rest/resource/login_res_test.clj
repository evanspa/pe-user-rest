(ns pe-user-rest.resource.login-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.pprint :refer (pprint)]
            [datomic.api :refer [q db] :as d]
            [compojure.core :refer [defroutes ANY]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [ring.mock.request :as mock]
            [pe-datomic-utils.core :as ducore]
            [pe-apptxn-core.core :as apptxncore]
            [pe-user-rest.resource.login-res :as loginres]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-datomic-testutils.core :as dtucore]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-rest.apptxn :as userapptxn]
            [pe-user-rest.test-utils :refer [db-uri
                                             usermt-subtype-prefix
                                             user-partition
                                             user-schema-filename
                                             apptxn-logging-schema-filename
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-apptxn-id
                                             userhdr-useragent-device-make
                                             userhdr-useragent-device-os
                                             userhdr-useragent-device-os-version
                                             userhdr-establish-session
                                             entity-uri-prefix
                                             login-uri-template]]))
(def conn (atom nil))

(defn empty-embedded-resources-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   conn
   accept-format-ind
   user-entid]
  {})

(defn empty-links-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   user-entid]
  {})

(defroutes routes-with-empty-embedded-and-links
  (ANY login-uri-template
       []
       (loginres/login-res @conn
                           user-partition
                           user-partition
                           usermt-subtype-prefix
                           userhdr-auth-token
                           userhdr-error-mask
                           base-url
                           entity-uri-prefix
                           userhdr-apptxn-id
                           userhdr-useragent-device-make
                           userhdr-useragent-device-os
                           userhdr-useragent-device-os-version
                           empty-embedded-resources-fn
                           empty-links-fn)))

(defroutes routes-with-nonempty-embedded-and-links
  (ANY login-uri-template
       []
       (loginres/login-res @conn
                           user-partition
                           user-partition
                           usermt-subtype-prefix
                           userhdr-auth-token
                           userhdr-error-mask
                           base-url
                           entity-uri-prefix
                           userhdr-apptxn-id
                           userhdr-useragent-device-make
                           userhdr-useragent-device-os
                           userhdr-useragent-device-os-version
                           (fn [version
                                base-url
                                entity-uri-prefix
                                entity-uri
                                conn
                                accept-format-ind
                                user-entid]
                             [{:spring :lambs}
                              {:meatball :sub}])
                           (fn [version
                                base-url
                                entity-uri-prefix
                                entity-uri
                                user-entid]
                             (-> {}
                                 (rucore/assoc-link
                                  (rucore/make-abs-link version
                                                        :fruit
                                                        "vnd.fruit"
                                                        base-url
                                                        (format "%s%s"
                                                                entity-uri-prefix
                                                                "pears/142"))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware-decorated app
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def app-with-empty-embedded-and-links
  (-> routes-with-empty-embedded-and-links
      (handler/api)
      (wrap-cookies)))

(def app-with-nonempty-embedded-and-links
  (-> routes-with-nonempty-embedded-and-links
      (handler/api)
      (wrap-cookies)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (dtucore/make-db-refresher-fixture-fn db-uri
                                                          conn
                                                          user-partition
                                                          [user-schema-filename
                                                           apptxn-logging-schema-filename]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- save-new-authtoken
  [conn u-entid expiration-date]
  (let [[token txnmap] (usercore/create-and-save-auth-token-txnmap user-partition
                                                               u-entid
                                                               expiration-date)
        tx @(d/transact conn [txnmap])]
    (d/resolve-tempid (d/db conn) (:tempids tx) (:db/id txnmap))))

(defn- save-new-user
  [conn user]
  (ducore/save-new-entity conn (usercore/save-new-user-txnmap user-partition user)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- assert-success-login
  [app credentials expected-embedded expected-links]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template
                                        userhdr-apptxn-id
                                        userhdr-useragent-device-make
                                        userhdr-useragent-device-os
                                        userhdr-useragent-device-os-version)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 200 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))
    (testing "headers and body of created user"
      (let [hdrs (:headers resp)
            resp-body-stream (:body resp)
            user-location-str (get hdrs "location")
            user-last-modified-str (get hdrs "last-modified")]
        (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
        (is (not (nil? resp-body-stream)))
        (is (not (nil? user-last-modified-str)))
        (let [last-modified (ucore/rfc7231str->instant user-last-modified-str)
              resp-user-entid-str (rtucore/last-url-part user-location-str)
              pct (rucore/parse-media-type (get hdrs "Content-Type"))
              charset (get rumeta/char-sets (:charset pct))
              resp-user (rucore/read-res pct resp-body-stream charset)
              embedded (get resp-user "_embedded")
              links (get resp-user "_links")
              auth-token (get hdrs userhdr-auth-token)]
          (is (= expected-embedded embedded))
          (is (= expected-links links))
          (is (not (nil? auth-token)))
          (is (not (nil? last-modified)))
          (is (not (nil? resp-user-entid-str)))
          (is (not (nil? resp-user)))
          (is (= "Karen Smith" (get resp-user "user/name")))
          (is (= "smithka@testing.com" (get resp-user "user/email")))
          (is (= "smithk" (get resp-user "user/username")))
          (is (nil? (get resp-user "user/password")))
          (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken @conn
                                                                                     (Long/parseLong resp-user-entid-str)
                                                                                     auth-token)]
            (is (not (nil? loaded-user-entid)))
            (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))
          (let [apptxns (apptxncore/all-apptxns @conn)
                _ (is (= 1 (count apptxns)))
                apptxn (first apptxns)
                apptxnlogs (:apptxn/logs apptxn)
                apptxnlogs (sort-by :apptxnlog/timestamp (vec apptxnlogs))]
            (is (= userapptxn/apptxn-user-login (:apptxn/usecase apptxn)))
            (is (= "iPhone" (:apptxn/user-agent-device-make apptxn)))
            (is (= "iOS" (:apptxn/user-agent-device-os apptxn)))
            (is (= "8.1.2" (:apptxn/user-agent-device-os-version apptxn)))
            (is (= 2 (count apptxnlogs)))
            (is (= 3 (:apptxnlog/usecase-event (first apptxnlogs))))
            (is (not (nil? (:apptxnlog/timestamp (first apptxnlogs)))))
            (is (nil? (:apptxnlog/edn-ctx (first apptxnlogs))))
            (is (nil? (:apptxnlog/in-ctx-err-desc (first apptxnlogs))))
            (is (= 6 (:apptxnlog/usecase-event (second apptxnlogs))))
            (is (not (nil? (:apptxnlog/timestamp (second apptxnlogs)))))
            (is (nil? (:apptxnlog/edn-ctx (second apptxnlogs))))
            (is (nil? (:apptxnlog/in-ctx-err-desc (second apptxnlogs))))))))))

(defn- assert-unauthorized-login
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template
                                        userhdr-apptxn-id
                                        userhdr-useragent-device-make
                                        userhdr-useragent-device-os
                                        userhdr-useragent-device-os-version)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 401 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))))

(defn- assert-malformed-login
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template
                                        userhdr-apptxn-id
                                        userhdr-useragent-device-make
                                        userhdr-useragent-device-os
                                        userhdr-useragent-device-os-version)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 400 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))))

(deftest user-success-login-by-username-with-empty-embedded
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Successful user login with app txn logs with emtpy embedded."
    (assert-success-login app-with-empty-embedded-and-links
                          {"user/username-or-email" "smithk"
                           "user/password" "insecure"}
                          {}
                          {})))

(deftest user-success-login-by-username-with-nonempty-embedded
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Successful user login with app txn logs with nonemtpy embedded."
    (assert-success-login app-with-nonempty-embedded-and-links
                          {"user/username-or-email" "smithk"
                           "user/password" "insecure"}
                          [{"spring" "lambs"}
                           {"meatball" "sub"}]
                          {"fruit" {"href" "/testing/pears/142"
                                    "type" "application/vnd.fruit-v0.0.1"}})))

(deftest user-success-login-by-email
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Successful user login with app txn logs."
    (assert-success-login app-with-empty-embedded-and-links
                          {"user/username-or-email" "smithka@testing.com"
                           "user/password" "insecure"}
                          {}
                          {})))

(deftest unsuccessful-login-wrong-password
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithk"
                                "user/password" "in5ecure"})))

(deftest unsuccessful-login-no-users-in-db
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithk"
                                "user/password" "insecure"})))

(deftest unsuccessful-login-wrong-username
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithka"
                                "user/password" "insecure"})))

(deftest unsuccessful-login-malformed-0
  (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username @conn "smithk")))
  (save-new-user @conn {:user/name "Karen Smith"
                        :user/email "smithka@testing.com"
                        :user/username "smithk"
                        :user/password "insecure"})
  (testing "Unsuccessful malformed user login with app txn logs 0."
    (assert-malformed-login app-with-empty-embedded-and-links
                            {"user/username-or-email" "smithka"})))
