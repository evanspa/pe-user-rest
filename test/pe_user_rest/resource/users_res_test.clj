(ns pe-user-rest.resource.users-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [compojure.core :refer [defroutes ANY]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [ring.mock.request :as mock]
            [clojure.java.jdbc :as j]
            [pe-user-rest.resource.users-res :as userres]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]
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
                                             users-uri-template
                                             db-spec-without-db
                                             db-spec
                                             db-name]]))

(defn embedded-resources-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   conn
   accept-format-ind
   user-entid]
  {})

(defn links-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   user-entid]
  {})

(defroutes routes
  (ANY users-uri-template
       []
       (userres/users-res db-spec
                          usermt-subtype-prefix
                          userhdr-auth-token
                          userhdr-error-mask
                          base-url
                          entity-uri-prefix
                          userhdr-establish-session
                          embedded-resources-fn
                          links-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware-decorated app
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def app
  (-> routes
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
                                        uddl/v0-create-authentication-token-ddl)
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-updated-count-inc-trigger-function-fn db-spec))
                      (jcore/with-try-catch-exec-as-query db-spec
                        (uddl/v0-create-user-account-updated-count-trigger-fn db-spec))
                      (f)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest success-user-creation-with-non-nil-req
  (testing "Successful creation of user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
                "user/username" "smithk"
                "user/created-at" (c/to-long (t/now))
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "user/created-at"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (= "smithka@testing.com" (get resp-user "user/email")))
            (is (= "smithk" (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken db-spec
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))))))))

(deftest success-user-creation-with-nil-username-req
  (testing "Successful creation of user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
                "user/created-at" (c/to-long (t/now))
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "user/created-at"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (= "smithka@testing.com" (get resp-user "user/email")))
            (is (nil? (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken db-spec
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))))))))

(deftest success-user-creation-with-nil-email-req
  (testing "Successful creation of user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/username" "smithk"
                "user/created-at" (c/to-long (t/now))
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "user/created-at"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (nil? (get resp-user "user/email")))
            (is (= "smithk" (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken db-spec
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))))))))

(deftest failed-user-creation-pre-existing-email
  (testing "Unsuccessful creation of user."
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
    (let [user {"user/name" "Karen K. Smith"
                "user/username" "karenksmith"
                "user/email" "smithka@testing.com"
                "user/created-at" (c/to-long (t/now))
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 403 (:status resp))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (nil? user-location-str))
          (let [error-mask-str (get hdrs userhdr-error-mask)]
            (is (nil? (get hdrs userhdr-auth-token)))
            (is (not (nil? error-mask-str)))
            (is (nil? (usercore/load-user-by-username db-spec "karenksmith")))
            (let [error-mask (Long/parseLong error-mask-str)]
              (is (pos? (bit-and error-mask userval/snu-any-issues)))
              (is (pos? (bit-and error-mask userval/snu-email-already-registered))))))))))

(deftest failed-user-creation-pre-existing-username
  (testing "Unsuccessful creation of user."
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
    (let [user {"user/name" "Karen K. Smith"
                "user/username" "smithk"
                "user/email" "karenksmith@testing.com"
                "user/created-at" (c/to-long (t/now))
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 403 (:status resp))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (nil? user-location-str))
          (let [error-mask-str (get hdrs userhdr-error-mask)]
            (is (nil? (get hdrs userhdr-auth-token)))
            (is (not (nil? error-mask-str)))
            (is (nil? (usercore/load-user-by-email db-spec "karenksmith@testing.com")))
            (log/debug "error-mask-str: " error-mask-str)
            (let [error-mask (Long/parseLong error-mask-str)]
              (is (pos? (bit-and error-mask userval/snu-any-issues)))
              (is (pos? (bit-and error-mask userval/snu-username-already-registered))))))))))

(deftest failed-user-creation-simulated-server-error
  (testing "Unsuccessful creation of user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (with-redefs [usercore/load-user-by-email (fn [conn email] (throw (Exception. "exception")))]
      (let [user {"user/name" "Karen Smith"
                  "user/email" "smithka@testing.com"
                  "user/created-at" (c/to-long (t/now))
                  "user/username" "smithk"
                  "user/password" "insecure"}
            req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                            meta/v001
                                            "UTF-8;q=1,ISO-8859-1;q=0"
                                            "json"
                                            "en-US"
                                            :post
                                            users-uri-template)
                    (rtucore/header userhdr-establish-session "true")
                    (mock/body (json/write-str user))
                    (mock/content-type (rucore/content-type rumeta/mt-type
                                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                                            meta/v001
                                                            "json"
                                                            "UTF-8")))
            resp (app req)]
        (testing "status code" (is (= 500 (:status resp))))))
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))))
