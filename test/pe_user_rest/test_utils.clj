(ns pe-user-rest.test-utils
  (:require [pe-user-rest.resource.version.login-res-v001]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-user-rest.resource.version.logout-res-v001]
            [ring.util.codec :refer [url-encode]]
            [pe-user-rest.meta :as meta]
            [clojure.java.jdbc :as j]
            [clojurewerkz.mailer.core :refer [delivery-mode!]]
            [pe-jdbc-utils.core :as jcore]
            [pe-user-core.ddl :as uddl]
            [pe-user-core.core :as usercore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]))

(delivery-mode! :test)

(def db-name "test_db")

(defn db-spec-fn
  ([]
   (db-spec-fn nil))
  ([db-name]
   (let [subname-prefix "//localhost:5432/"]
     {:classname "org.postgresql.Driver"
      :subprotocol "postgresql"
      :subname (if db-name
                 (str subname-prefix db-name)
                 subname-prefix)
      :user "postgres"})))

(def db-spec-without-db (db-spec-fn nil))

(def db-spec (db-spec-fn db-name))

(defn fixture-maker
  []
  (fn [f]
    ;; Database setup
    (jcore/drop-database db-spec-without-db db-name)
    (jcore/create-database db-spec-without-db db-name)

    ;; User / auth-token setup
    (j/db-do-commands db-spec
                      true
                      uddl/schema-version-ddl
                      uddl/v0-create-user-account-ddl
                      uddl/v0-add-unique-constraint-user-account-email
                      uddl/v0-add-unique-constraint-user-account-username
                      uddl/v0-create-authentication-token-ddl
                      uddl/v1-user-add-deleted-reason-col
                      uddl/v1-user-add-suspended-at-col
                      uddl/v1-user-add-suspended-reason-col
                      uddl/v1-user-add-suspended-count-col
                      uddl/v2-create-email-verification-token-ddl
                      uddl/v3-create-password-reset-token-ddl)
    (jcore/with-try-catch-exec-as-query db-spec
      (uddl/v0-create-updated-count-inc-trigger-fn db-spec))
    (jcore/with-try-catch-exec-as-query db-spec
      (uddl/v0-create-user-account-updated-count-trigger-fn db-spec))
    (jcore/with-try-catch-exec-as-query db-spec
      (uddl/v1-create-suspended-count-inc-trigger-fn db-spec))
    (jcore/with-try-catch-exec-as-query db-spec
      (uddl/v1-create-user-account-suspended-count-trigger-fn db-spec))
    (f)))

(def usermt-subtype-prefix "vnd.")
(def user-auth-scheme "user-auth")
(def user-auth-scheme-param-name "user-user-token")
(def userhdr-auth-token "user-rest-auth-token")
(def userhdr-error-mask "user-rest-error-mask")
(def userhdr-if-unmodified-since "user-if-unmodified-since")
(def userhdr-if-modified-since "user-if-modified-since")
(def userhdr-login-failed-reason "user-login-failed-reason")
(def userhdr-delete-reason "user-delete-reason")
(def base-url "")
(def entity-uri-prefix "/testing/")
(def userhdr-establish-session "user-establish-session")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Account verification related
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def welcome-and-verification-email-mustache-template "email/templates/welcome-and-account-verification.html.mustache")
(def verification-email-mustache-template "email/templates/account-verification.html.mustache")
(def welcome-and-verification-email-subject-line "welcome and account verification")
(def welcome-and-verification-email-from "welcome@example.com")

(defn verification-url-maker
  [user-id verification-token]
  (str base-url entity-uri-prefix meta/pathcomp-users user-id "/" meta/pathcomp-verification "/" verification-token))

(defn verification-flagged-url-maker
  [user-id verification-token]
  (str base-url entity-uri-prefix meta/pathcomp-users user-id "/" meta/pathcomp-verification-flagged "/" verification-token))

(def verification-success-mustache-template "web/templates/verification-success.html.mustache")
(def verification-error-mustache-template "web/templates/verification-error.html.mustache")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Password reset related
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def password-reset-email-mustache-template "email/templates/password-reset.html.mustache")
(def password-reset-email-subject-line "password reset")
(def password-reset-email-from "password-reset@example.com")

(defn password-reset-url-maker
  [email password-reset-token]
  (url-encode (str base-url
                   entity-uri-prefix
                   meta/pathcomp-users
                   email
                   "/"
                   meta/pathcomp-send-password-reset-email
                   "/"
                   password-reset-token)))

(defn password-reset-flagged-url-maker
  [email password-reset-token]
  (url-encode (str base-url
                   entity-uri-prefix
                   meta/pathcomp-users
                   email
                   "/"
                   meta/pathcomp-password-reset-flagged
                   "/"
                   password-reset-token)))

(def password-reset-success-mustache-template "web/templates/password-reset-success.html.mustache")
(def password-reset-error-mustache-template "web/templates/password-reset-error.html.mustache")

(def users-uri-template
  (rucore/make-abs-link-href base-url (format "%s%s" entity-uri-prefix meta/pathcomp-users)))

(def user-uri-template
  (format "%s%s%s/:user-id"
          base-url
          entity-uri-prefix
          meta/pathcomp-users))

(def verification-uri-template
  (format "%s%s%s/:email/%s/:verification-token"
          base-url
          entity-uri-prefix
          meta/pathcomp-users
          meta/pathcomp-verification))

(def send-password-reset-email-uri-template
  (format "%s%s/%s"
          base-url
          entity-uri-prefix
          meta/pathcomp-send-password-reset-email))

(def prepare-password-reset-uri-template
  (format "%s%s%s/:email/%s/:password-reset-token"
          base-url
          entity-uri-prefix
          meta/pathcomp-users
          meta/pathcomp-prepare-password-reset))

(def password-reset-uri-template
  (format "%s%s%s/:email/%s/:password-reset-token"
          base-url
          entity-uri-prefix
          meta/pathcomp-users
          meta/pathcomp-password-reset))

(def login-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s%s"
                                     entity-uri-prefix
                                     meta/pathcomp-login)))

(def logout-uri-template
  (format "%s%s%s/:user-id/%s"
          base-url
          entity-uri-prefix
          meta/pathcomp-users
          meta/pathcomp-logout))

(def light-login-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s%s"
                                     entity-uri-prefix
                                     meta/pathcomp-light-login)))
