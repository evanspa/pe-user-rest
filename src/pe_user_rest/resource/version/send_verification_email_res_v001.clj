(ns pe-user-rest.resource.version.send-verification-email-res-v001
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
            [pe-user-rest.resource.send-verification-email-res :refer [body-data-in-transform-fn
                                                                       do-send-verification-email-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   user-id
   post-as-do-send-verification-email-input]
  (identity post-as-do-send-verification-email-input))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do send verification email function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-send-verification-email-fn meta/v001
  [version
   db-spec
   user-id
   verification-email-mustache-template
   verification-email-subject-line
   verification-email-from
   verification-url-maker-fn
   flagged-url-maker-fn]
  (usercore/send-verification-notice db-spec
                                     user-id
                                     verification-email-mustache-template
                                     verification-email-subject-line
                                     verification-email-from
                                     verification-url-maker-fn
                                     flagged-url-maker-fn)
  {:status 204})
