(ns pe-user-rest.resource.password-reset-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.tools.logging :as log]
            [clojure.java.jdbc :as j]
            [ring.util.codec :refer [url-decode url-encode]]
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
            [pe-user-rest.resource.login-res :as loginres]
            [pe-user-rest.resource.password-reset-util :as pwdresetutil]
            [pe-user-rest.resource.password-reset-res :as passwordresetres]
            [pe-user-rest.meta :as meta]
            [pe-user-core.ddl :as uddl]
            [pe-jdbc-utils.core :as jcore]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]
            [pe-user-rest.test-utils :refer [usermt-subtype-prefix
                                             user-auth-scheme
                                             user-auth-scheme-param-name
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-establish-session
                                             userhdr-if-unmodified-since
                                             userhdr-if-modified-since
                                             userhdr-login-failed-reason
                                             userhdr-delete-reason
                                             entity-uri-prefix
                                             user-uri-template
                                             password-reset-uri-template
                                             users-uri-template
                                             login-uri-template
                                             fixture-maker
                                             welcome-and-verification-email-mustache-template
                                             welcome-and-verification-email-subject-line
                                             welcome-and-verification-email-from
                                             verification-url-maker
                                             verification-flagged-url-maker
                                             new-user-notification-mustache-template
                                             new-user-notification-from-email
                                             new-user-notification-to-email
                                             new-user-notification-subject
                                             password-reset-email-mustache-template
                                             password-reset-email-subject-line
                                             password-reset-email-from
                                             prepare-password-reset-url-maker
                                             password-reset-flagged-url-maker
                                             password-reset-success-mustache-template
                                             password-reset-error-mustache-template
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
                           empty-links-fn
                           welcome-and-verification-email-mustache-template
                           welcome-and-verification-email-subject-line
                           welcome-and-verification-email-from
                           verification-url-maker
                           verification-flagged-url-maker
                           new-user-notification-mustache-template
                           new-user-notification-from-email
                           new-user-notification-to-email
                           new-user-notification-subject
                           err-notification-mustache-template
                           err-subject
                           err-from-email
                           err-to-email))

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
                         empty-links-fn
                         userhdr-if-unmodified-since
                         userhdr-if-modified-since
                         userhdr-delete-reason
                         err-notification-mustache-template
                         err-subject
                         err-from-email
                         err-to-email))
  (ANY password-reset-uri-template
       [email
        password-reset-token]
       (passwordresetres/password-reset-res db-spec
                                            base-url
                                            entity-uri-prefix
                                            (url-decode email)
                                            password-reset-token
                                            password-reset-success-mustache-template
                                            password-reset-error-mustache-template
                                            err-notification-mustache-template
                                            err-subject
                                            err-from-email
                                            err-to-email))
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
                           err-to-email)))

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
(use-fixtures :each (fixture-maker))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest integration-tests-1
  (testing "Successful creation of user and verifying the user."
    (is (nil? (usercore/load-user-by-email db-spec "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username db-spec "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
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
        ;; Perform password reset
        (let [password-reset-token (usercore/create-and-save-password-reset-token db-spec
                                                                                  (Long. resp-user-id-str)
                                                                                  "smithka@testing.com")]
          ; before we can reset the password via the post, we need to 'prepare' first
          (usercore/prepare-password-reset db-spec "smithka@testing.com" password-reset-token)
          (let [password-reset-uri (str base-url
                                        entity-uri-prefix
                                        meta/pathcomp-users
                                        "/"
                                        (url-encode "smithka@testing.com")
                                        "/"
                                        meta/pathcomp-password-reset
                                        "/"
                                        password-reset-token)
                req (-> (rtucore/req-w-std-hdrs "text"
                                                "html"
                                                nil
                                                "UTF-8;q=1,ISO-8859-1;q=0"
                                                nil
                                                "en-US"
                                                :post
                                                password-reset-uri)
                        (mock/body (format "%s=againInsecure" pwdresetutil/param-new-password))
                        (mock/content-type "application/x-www-form-urlencoded"))
                resp (app req)
                hdrs (:headers resp)]
            (testing "status code" (is (= 200 (:status resp))))
            ;; Load user from database using new password
            (is (nil? (usercore/authenticate-user-by-password db-spec "smithka@testing.com" "insecure")))
            (let [[loaded-user-id loaded-user] (usercore/authenticate-user-by-password db-spec "smithka@testing.com" "againInsecure")]
              (is (not (nil? loaded-user-id)))
              (is (= (Long/parseLong resp-user-id-str) loaded-user-id))
              (is (= "Karen Smith" (:user/name loaded-user)))
              (is (= "smithka@testing.com" (:user/email loaded-user))))))))))
