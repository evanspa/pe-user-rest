(ns pe-user-rest.resource.login-logout-res-test
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
            [pe-user-rest.resource.logout-res :as logoutres]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-user-core.ddl :as uddl]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-jdbc-utils.core :as jcore]
            [pe-user-rest.test-utils :refer [usermt-subtype-prefix
                                             user-auth-scheme
                                             user-auth-scheme-param-name
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-establish-session
                                             userhdr-login-failed-reason
                                             entity-uri-prefix
                                             login-uri-template
                                             light-login-uri-template
                                             logout-uri-template
                                             fixture-maker
                                             err-notification-mustache-template
                                             err-subject
                                             err-from-email
                                             err-to-email
                                             db-spec-without-db
                                             db-spec
                                             db-name]]
            [pe-user-rest.resource.user-test-utils :refer [user-id-and-token-for-credentials
                                                           assert-success-login
                                                           assert-success-light-login
                                                           assert-unauthorized-login
                                                           assert-unauthorized-light-login
                                                           assert-malformed-login]]))
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
                           empty-links-fn
                           userhdr-login-failed-reason
                           err-notification-mustache-template
                           err-subject
                           err-from-email
                           err-to-email))
  (ANY light-login-uri-template
       []
       (loginres/light-login-res db-spec
                                 usermt-subtype-prefix
                                 userhdr-auth-token
                                 userhdr-error-mask
                                 base-url
                                 entity-uri-prefix
                                 userhdr-login-failed-reason
                                 err-notification-mustache-template
                                 err-subject
                                 err-from-email
                                 err-to-email))
  (ANY logout-uri-template
       [user-id]
       (logoutres/logout-res db-spec
                             usermt-subtype-prefix
                             userhdr-auth-token
                             userhdr-error-mask
                             user-auth-scheme
                             user-auth-scheme-param-name
                             base-url
                             entity-uri-prefix
                             (Long. user-id)
                             err-notification-mustache-template
                             err-subject
                             err-from-email
                             err-to-email)))

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
                                                                "pears/142")))))
                           userhdr-login-failed-reason
                           err-notification-mustache-template
                           err-subject
                           err-from-email
                           err-to-email))
  (ANY light-login-uri-template
       []
       (loginres/light-login-res db-spec
                                 usermt-subtype-prefix
                                 userhdr-auth-token
                                 userhdr-error-mask
                                 base-url
                                 entity-uri-prefix
                                 userhdr-login-failed-reason
                                 err-notification-mustache-template
                                 err-subject
                                 err-from-email
                                 err-to-email))
  (ANY logout-uri-template
       [user-id]
       (logoutres/logout-res db-spec
                             usermt-subtype-prefix
                             userhdr-auth-token
                             userhdr-error-mask
                             user-auth-scheme
                             user-auth-scheme-param-name
                             base-url
                             entity-uri-prefix
                             (Long. user-id)
                             err-notification-mustache-template
                             err-subject
                             err-from-email
                             err-to-email)))

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
(use-fixtures :each (fixture-maker))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest user-success-login-by-username-with-empty-embedded
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
                          {})
    (assert-success-light-login app-with-empty-embedded-and-links
                                {"user/username-or-email" "smithk"
                                 "user/password" "insecure"})))

(deftest user-success-login-by-username-with-nonempty-embedded
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

(deftest user-success-login-by-email
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
                          {})
    (assert-success-light-login app-with-empty-embedded-and-links
                                {"user/username-or-email" "smithka@testing.com"
                                 "user/password" "insecure"})))

(deftest logout-success-1
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
  (let [[user-id-str auth-token] (user-id-and-token-for-credentials app-with-empty-embedded-and-links
                                                                {"user/username-or-email" "smithka@testing.com"
                                                                 "user/password" "insecure"})]
    (is (not (nil? user-id-str)))
    (is (not (nil? auth-token)))
    (let [[loaded-user-id loaded-user] (usercore/load-user-by-authtoken db-spec
                                                                        (Long/parseLong user-id-str)
                                                                        auth-token)]
      (is (not (nil? loaded-user-id)))
      (is (= (Long/parseLong user-id-str) loaded-user-id))
      (let [logout-uri (str base-url
                            entity-uri-prefix
                            meta/pathcomp-users
                            "/"
                            user-id-str
                            "/"
                            meta/pathcomp-logout)
            req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                            meta/v001
                                            "UTF-8;q=1,ISO-8859-1;q=0"
                                            "json"
                                            "en-US"
                                            :post
                                            logout-uri)
                    (mock/body (json/write-str {:logout :logout}))
                    (mock/content-type (rucore/content-type rumeta/mt-type
                                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                                            meta/v001
                                                            "json"
                                                            "UTF-8"))
                    (rtucore/header "Authorization" (rtucore/authorization-req-hdr-val user-auth-scheme
                                                                                       user-auth-scheme-param-name
                                                                                       auth-token)))
            resp (app-with-empty-embedded-and-links req)]
        (testing "status code" (is (= 204 (:status resp))))
        (is (nil? (usercore/load-user-by-authtoken db-spec
                                                   (Long/parseLong user-id-str)
                                                   auth-token)))))))

(deftest unsuccessful-login-wrong-password
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
                                "user/password" "in5ecure"}
                               nil
                               userhdr-login-failed-reason)
    (assert-unauthorized-light-login app-with-empty-embedded-and-links
                                     {"user/username-or-email" "smithk"
                                      "user/password" "in5ecure"}
                                     nil
                                     userhdr-login-failed-reason)))

(deftest unsuccessful-login-no-users-in-db
  (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
  (is (nil? (usercore/load-user-by-username db-spec "smithk")))
  (testing "Unsuccessful user login with app txn logs."
    (assert-unauthorized-login app-with-empty-embedded-and-links
                               {"user/username-or-email" "smithk"
                                "user/password" "insecure"}
                               nil
                               userhdr-login-failed-reason)
    (assert-unauthorized-light-login app-with-empty-embedded-and-links
                                     {"user/username-or-email" "smithk"
                                      "user/password" "insecure"}
                                     nil
                                     userhdr-login-failed-reason)))

(deftest unsuccessful-login-wrong-username
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
                                "user/password" "insecure"}
                               nil
                               userhdr-login-failed-reason)
    (assert-unauthorized-light-login app-with-empty-embedded-and-links
                                     {"user/username-or-email" "smithka"
                                      "user/password" "insecure"}
                                     nil
                                     userhdr-login-failed-reason)))

(deftest unsuccessful-login-malformed-0
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
