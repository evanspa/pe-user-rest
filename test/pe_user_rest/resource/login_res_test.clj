(ns pe-user-rest.resource.login-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.pprint :refer (pprint)]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes ANY]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [ring.mock.request :as mock]
            [clojure.java.jdbc :as j]
            [pe-user-rest.resource.login-res :as loginres]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-user-core.ddl :as uddl]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-jdbc-utils.core :as jcore]
            [pe-user-rest.test-utils :refer [usermt-subtype-prefix
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-establish-session
                                             entity-uri-prefix
                                             login-uri-template
                                             db-spec-without-db
                                             db-spec
                                             db-name]]))
(defn empty-embedded-resources-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   db-spec
   accept-format-ind
   user-id]
  {})

(defn empty-links-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   user-id]
  {})

(defroutes routes-with-empty-embedded-and-links
  (ANY login-uri-template
       []
       (loginres/login-res db-spec
                           usermt-subtype-prefix
                           userhdr-auth-token
                           userhdr-error-mask
                           base-url
                           entity-uri-prefix
                           empty-embedded-resources-fn
                           empty-links-fn)))

(defroutes routes-with-nonempty-embedded-and-links
  (ANY login-uri-template
       []
       (loginres/login-res db-spec
                           usermt-subtype-prefix
                           userhdr-auth-token
                           userhdr-error-mask
                           base-url
                           entity-uri-prefix
                           (fn [version
                                base-url
                                entity-uri-prefix
                                entity-uri
                                db-spec
                                accept-format-ind
                                user-id]
                             [{:spring :lambs}
                              {:meatball :sub}])
                           (fn [version
                                base-url
                                entity-uri-prefix
                                entity-uri
                                user-id]
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
(use-fixtures :each (fn [f]
                      (jcore/drop-database db-spec-without-db db-name)
                      (jcore/create-database db-spec-without-db db-name)
                      (j/db-do-commands db-spec
                                        true
                                        uddl/schema-version-ddl
                                        uddl/v0-create-user-account-ddl
                                        uddl/v0-add-unique-constraint-user-account-email
                                        uddl/v0-add-unique-constraint-user-account-username
                                        uddl/v0-create-authentication-token-ddl
                                        uddl/v0-add-column-user-account-updated-w-auth-token)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-user-account-updated-count-trigger-fn db-spec))
                      (f)))

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
                                        login-uri-template)
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
            user-location-str (get hdrs "location")]
        (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
        (is (not (nil? resp-body-stream)))
        (let [resp-user-id-str (rtucore/last-url-part user-location-str)
              pct (rucore/parse-media-type (get hdrs "Content-Type"))
              charset (get rumeta/char-sets (:charset pct))
              resp-user (rucore/read-res pct resp-body-stream charset)
              embedded (get resp-user "_embedded")
              links (get resp-user "_links")
              auth-token (get hdrs userhdr-auth-token)]
          (is (= expected-embedded embedded))
          (is (= expected-links links))
          (is (not (nil? auth-token)))
          (is (not (nil? resp-user-id-str)))
          (is (not (nil? resp-user)))
          (is (not (nil? (get resp-user "user/created-at"))))
          (is (= "Karen Smith" (get resp-user "user/name")))
          (is (= "smithka@testing.com" (get resp-user "user/email")))
          (is (= "smithk" (get resp-user "user/username")))
          (is (nil? (get resp-user "user/password")))
          (let [[loaded-user-id loaded-user] (usercore/load-user-by-authtoken db-spec
                                                                              (Long/parseLong resp-user-id-str)
                                                                              auth-token)]
            (is (not (nil? loaded-user-id)))
            (is (= (Long/parseLong resp-user-id-str) loaded-user-id))))))))

(defn- assert-unauthorized-login
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template)
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
                                        login-uri-template)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 400 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))))

#_(deftest user-success-login-by-username-with-empty-embedded
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user conn
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Successful user login with app txn logs with emtpy embedded."
    (assert-success-login app-with-empty-embedded-and-links
                          {"user/username-or-email" "smithk"
                           "user/password" "insecure"}
                          {}
                          {})))

#_(deftest user-success-login-by-username-with-nonempty-embedded
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user db-spec
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Successful user login with app txn logs with nonemtpy embedded."
    (assert-success-login app-with-nonempty-embedded-and-links
                          {"user/username-or-email" "smithk"
                           "user/password" "insecure"}
                          [{"spring" "lambs"}
                           {"meatball" "sub"}]
                          {"fruit" {"href" "/testing/pears/142"
                                    "type" "application/vnd.fruit-v0.0.1"}})))

#_(deftest user-success-login-by-email
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user db-spec
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Successful user login with app txn logs."
    (assert-success-login app-with-empty-embedded-and-links
                          {"user/username-or-email" "smithka@testing.com"
                           "user/password" "insecure"}
                          {}
                          {})))

#_(deftest unsuccessful-login-wrong-password
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user db-spec
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithk"
                                "user/password" "in5ecure"})))

#_(deftest unsuccessful-login-no-users-in-db
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithk"
                                "user/password" "insecure"})))

#_(deftest unsuccessful-login-wrong-username
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user db-spec
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithka"
                                "user/password" "insecure"})))

#_(deftest unsuccessful-login-malformed-0
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (j/with-db-transaction [conn db-spec]
    (let [new-user-id (usercore/next-user-account-id conn)]
      (usercore/save-new-user db-spec
                              new-user-id
                              {:user/name "Karen Smith"
                               :user/email "smithka@testing.com"
                               :user/username "smithk"
                               :user/created-at (c/to-long (t/now))
                               :user/password "insecure"})))
  (testing "Unsuccessful malformed user login with app txn logs 0."
    (assert-malformed-login app-with-empty-embedded-and-links
                            {"user/username-or-email" "smithka"})))
