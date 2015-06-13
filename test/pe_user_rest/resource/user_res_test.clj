(ns pe-user-rest.resource.user-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as j]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [compojure.core :refer [defroutes ANY]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [ring.mock.request :as mock]
            [pe-user-rest.resource.users-res :as usersres]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-user-rest.resource.user-res :as userres]
            [pe-user-rest.resource.version.user-res-v001]
            [pe-user-rest.meta :as meta]
            [pe-user-core.ddl :as uddl]
            [pe-jdbc-utils.core :as jcore]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-rest.meta :as usermeta]
            [pe-user-core.validation :as userval]
            [pe-user-rest.test-utils :refer [usermt-subtype-prefix
                                             user-auth-scheme
                                             user-auth-scheme-param-name
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-establish-session
                                             entity-uri-prefix
                                             user-uri-template
                                             users-uri-template
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

(defroutes routes
  (ANY users-uri-template
       []
       (usersres/users-res db-spec
                           usermt-subtype-prefix
                           userhdr-auth-token
                           userhdr-error-mask
                           base-url
                           entity-uri-prefix
                           userhdr-establish-session
                           empty-embedded-resources-fn
                           empty-links-fn))

  (ANY user-uri-template
       [user-id]
       (userres/user-res db-spec
                         usermt-subtype-prefix
                         userhdr-auth-token
                         userhdr-error-mask
                         user-auth-scheme
                         user-auth-scheme-param-name
                         base-url
                         entity-uri-prefix
                         (Long. user-id)
                         empty-embedded-resources-fn
                         empty-links-fn)))

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
(deftest integration-tests-1
  (testing "Successful creation of user and updating the user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
                "user/username" "smithk"
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (usermeta/mt-subtype-user usermt-subtype-prefix)
                                          usermeta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (usermeta/mt-subtype-user usermt-subtype-prefix)
                                                          usermeta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (let [hdrs (:headers resp)
            resp-body-stream (:body resp)
            user-location-str (get hdrs "location")
            resp-user-id-str (rtucore/last-url-part user-location-str)
            pct (rucore/parse-media-type (get hdrs "Content-Type"))
            charset (get rumeta/char-sets (:charset pct))
            resp-user (rucore/read-res pct resp-body-stream charset)
            auth-token (get hdrs userhdr-auth-token)
            [loaded-user-id loaded-user] (usercore/load-user-by-authtoken db-spec
                                                                          (Long. resp-user-id-str)
                                                                          auth-token)]
        ;; Update the user
        (let [user {"user/name" "Kate A. Smithward"}
              user-uri (str base-url
                                entity-uri-prefix
                                usermeta/pathcomp-users
                                "/"
                                resp-user-id-str)
              req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                              meta/v001
                                              "UTF-8;q=1,ISO-8859-1;q=0"
                                              "json"
                                              "en-US"
                                              :put
                                              user-uri)
                      (mock/body (json/write-str user))
                      (mock/content-type (rucore/content-type rumeta/mt-type
                                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                                              meta/v001
                                                              "json"
                                                              "UTF-8"))
                      (rtucore/header "Authorization" (rtucore/authorization-req-hdr-val user-auth-scheme
                                                                                         user-auth-scheme-param-name
                                                                                         auth-token)))
              resp (app req)
              hdrs (:headers resp)]
          (testing "status code" (is (= 200 (:status resp))))
          (testing "body of updated user"
            (let [hdrs (:headers resp)
              resp-body-stream (:body resp)]
              (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
              (is (not (nil? resp-body-stream)))
              (let [pct (rucore/parse-media-type (get hdrs "Content-Type"))
                    charset (get rumeta/char-sets (:charset pct))
                    resp-user (rucore/read-res pct resp-body-stream charset)]
                (is (not (nil? resp-user)))
                (is (= "Kate A. Smithward" (get resp-user "user/name"))))))
          (testing "load updated user directly from database"
            (let [[loaded-user-id loaded-user] (usercore/load-user-by-id db-spec
                                                                         (Long/parseLong resp-user-id-str))]
              (is (not (nil? loaded-user-id)))
              (is (= (Long/parseLong resp-user-id-str) loaded-user-id))
              (is (= "Kate A. Smithward" (:user/name loaded-user)))
              (is (= "smithk" (:user/username loaded-user)))
              (is (= "smithka@testing.com" (:user/email loaded-user))))))
        ;; Update the user again
        (let [user {"user/name" "Kate A. Smithward II"
                    "user/username" "kates2"
                    "user/email" "kate@testing.com"}
              user-uri (str base-url
                                entity-uri-prefix
                                usermeta/pathcomp-users
                                "/"
                                resp-user-id-str)
              req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                              meta/v001
                                              "UTF-8;q=1,ISO-8859-1;q=0"
                                              "json"
                                              "en-US"
                                              :put
                                              user-uri)
                      (mock/body (json/write-str user))
                      (mock/content-type (rucore/content-type rumeta/mt-type
                                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                                              meta/v001
                                                              "json"
                                                              "UTF-8"))
                      (rtucore/header "Authorization" (rtucore/authorization-req-hdr-val user-auth-scheme
                                                                                         user-auth-scheme-param-name
                                                                                         auth-token)))
              resp (app req)
              hdrs (:headers resp)]
          (testing "status code" (is (= 200 (:status resp))))
          (testing "body of updated user"
            (let [hdrs (:headers resp)
              resp-body-stream (:body resp)]
              (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
              (is (not (nil? resp-body-stream)))
              (let [pct (rucore/parse-media-type (get hdrs "Content-Type"))
                    charset (get rumeta/char-sets (:charset pct))
                    resp-user (rucore/read-res pct resp-body-stream charset)]
                (is (not (nil? resp-user)))
                (is (= "Kate A. Smithward II" (get resp-user "user/name"))))))
          (testing "load updated user directly from database"
            (let [[loaded-user-id loaded-user] (usercore/load-user-by-id db-spec
                                                                         (Long/parseLong resp-user-id-str))]
              (is (not (nil? loaded-user-id)))
              (is (= (Long/parseLong resp-user-id-str) loaded-user-id))
              (is (= "Kate A. Smithward II" (:user/name loaded-user)))
              (is (= "kates2" (:user/username loaded-user)))
              (is (= "kate@testing.com" (:user/email loaded-user))))))
        ;; Update the user yet again
        (let [user {"user/name" "Kate A. Smithward II"
                    "user/username" "kates2"
                    "user/email" nil}
              user-uri (str base-url
                                entity-uri-prefix
                                usermeta/pathcomp-users
                                "/"
                                resp-user-id-str)
              req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                              meta/v001
                                              "UTF-8;q=1,ISO-8859-1;q=0"
                                              "json"
                                              "en-US"
                                              :put
                                              user-uri)
                      (mock/body (json/write-str user))
                      (mock/content-type (rucore/content-type rumeta/mt-type
                                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                                              meta/v001
                                                              "json"
                                                              "UTF-8"))
                      (rtucore/header "Authorization" (rtucore/authorization-req-hdr-val user-auth-scheme
                                                                                         user-auth-scheme-param-name
                                                                                         auth-token)))
              resp (app req)
              hdrs (:headers resp)]
          (testing "status code" (is (= 200 (:status resp))))
          (testing "body of updated user"
            (let [hdrs (:headers resp)
              resp-body-stream (:body resp)]
              (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
              (is (not (nil? resp-body-stream)))
              (let [pct (rucore/parse-media-type (get hdrs "Content-Type"))
                    charset (get rumeta/char-sets (:charset pct))
                    resp-user (rucore/read-res pct resp-body-stream charset)]
                (is (not (nil? resp-user)))
                (is (= "Kate A. Smithward II" (get resp-user "user/name"))))))
          (testing "load updated user directly from database"
            (let [[loaded-user-id loaded-user] (usercore/load-user-by-id db-spec
                                                                         (Long/parseLong resp-user-id-str))]
              (is (not (nil? loaded-user-id)))
              (is (= (Long/parseLong resp-user-id-str) loaded-user-id))
              (is (= "Kate A. Smithward II" (:user/name loaded-user)))
              (is (= "kates2" (:user/username loaded-user)))
              (is (nil? (:user/email loaded-user))))))
        ;; Update the user again with invalid request
        (let [user {"user/name" "Kate A. Smithward II"
                    "user/username" ""
                    "user/email" ""}
              user-uri (str base-url
                                entity-uri-prefix
                                usermeta/pathcomp-users
                                "/"
                                resp-user-id-str)
              req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                              meta/v001
                                              "UTF-8;q=1,ISO-8859-1;q=0"
                                              "json"
                                              "en-US"
                                              :put
                                              user-uri)
                      (mock/body (json/write-str user))
                      (mock/content-type (rucore/content-type rumeta/mt-type
                                                              (meta/mt-subtype-user usermt-subtype-prefix)
                                                              meta/v001
                                                              "json"
                                                              "UTF-8"))
                      (rtucore/header "Authorization" (rtucore/authorization-req-hdr-val user-auth-scheme
                                                                                         user-auth-scheme-param-name
                                                                                         auth-token)))
              resp (app req)
              hdrs (:headers resp)]
          (testing "status code" (is (= 422 (:status resp))))
          (testing "error info"
            (let [hdrs (:headers resp)
                  error-mask-str (get hdrs userhdr-error-mask)]
              (is (nil? (get hdrs userhdr-auth-token)))
              (is (not (nil? error-mask-str)))
              (let [error-mask (Long/parseLong error-mask-str)]
                (is (pos? (bit-and error-mask userval/su-any-issues)))
                (is (pos? (bit-and error-mask userval/su-username-and-email-not-provided)))))))))))