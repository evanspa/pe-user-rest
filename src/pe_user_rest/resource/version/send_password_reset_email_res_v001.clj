(ns pe-user-rest.resource.version.send-password-reset-email-res-v001
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [pe-user-rest.meta :as meta]
            [clojure.java.jdbc :as j]
            [clojure.tools.logging :as log]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]
            [pe-user-rest.meta :as meta]
            [pe-user-rest.resource.send-password-reset-email-res :refer [body-data-in-transform-fn
                                                                         do-send-password-reset-email-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   user-id
   post-as-do-send-password-reset-email-input]
  (identity post-as-do-send-password-reset-email-input))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do send password-reset email function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-send-password-reset-email-fn meta/v001
  [version
   db-spec
   send-password-reset-post-as-do-body
   password-reset-email-mustache-template
   password-reset-email-subject-line
   password-reset-email-from
   password-reset-url-maker-fn
   password-reset-flagged-url-maker-fn]
  (usercore/send-password-reset-notice db-spec
                                       (:user/email send-password-reset-post-as-do-body)
                                       password-reset-email-mustache-template
                                       password-reset-email-subject-line
                                       password-reset-email-from
                                       password-reset-url-maker-fn
                                       password-reset-flagged-url-maker-fn)
  {:status 204})
