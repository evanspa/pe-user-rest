(ns pe-user-rest.test-utils
  (:require [pe-user-rest.resource.version.login-res-v001]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-user-rest.meta :as meta]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]))

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

(def usermt-subtype-prefix "vnd.")
(def user-auth-scheme "user-auth")
(def user-auth-scheme-param-name "user-user-token")
(def userhdr-auth-token "user-rest-auth-token")
(def userhdr-error-mask "user-rest-error-mask")
(def base-url "")
(def entity-uri-prefix "/testing/")
(def userhdr-establish-session "user-establish-session")

(def users-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s/%s"
                                     entity-uri-prefix
                                     meta/pathcomp-users)))

(def user-uri-template
  (format "%s%s%s/:user-id"
          base-url
          entity-uri-prefix
          meta/pathcomp-users))

(def login-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s/%s"
                                     entity-uri-prefix
                                     meta/pathcomp-login)))
