(ns pe-user-rest.test-utils
  (:require [pe-user-rest.resource.version.login-res-v001]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-user-rest.meta :as meta]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]))

(def user-schema-filename "user-schema-updates-0.0.1.dtm")
(def apptxn-logging-schema-filename "apptxn-logging-schema-updates-0.0.1.dtm")
(def db-uri "datomic:mem://user")
(def user-partition :user)
(def usermt-subtype-prefix "vnd.")
(def userhdr-auth-token "user-rest-auth-token")
(def userhdr-error-mask "user-rest-error-mask")
(def userhdr-apptxn-id "user-apptxn-id")
(def userhdr-useragent-device-make "user-rest-useragent-device-make")
(def userhdr-useragent-device-os "user-rest-useragent-device-os")
(def userhdr-useragent-device-os-version "user-rest-useragent-device-os-version")
(def base-url "")
(def entity-uri-prefix "/testing/")
(def userhdr-establish-session "user-establish-session")

(def users-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s/%s"
                                     entity-uri-prefix
                                     meta/pathcomp-users)))

(def login-uri-template
  (rucore/make-abs-link-href base-url
                             (format "%s/%s"
                                     entity-uri-prefix
                                     meta/pathcomp-login)))
