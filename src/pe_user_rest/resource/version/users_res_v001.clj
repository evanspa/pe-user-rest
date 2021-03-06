(ns pe-user-rest.resource.version.users-res-v001
  (:require [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [pe-user-core.validation :as userval]
            [pe-user-rest.utils :as userresutils]
            [pe-user-rest.resource.users-res :refer [new-user-validator-fn
                                                     body-data-in-transform-fn
                                                     body-data-out-transform-fn
                                                     next-user-account-id-fn
                                                     save-new-user-fn
                                                     send-email-verification-fn
                                                     make-session-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Validator function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod new-user-validator-fn meta/v001
  [version user]
  (userval/save-new-user-validation-mask user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   user]
  (identity user))

(defmethod body-data-out-transform-fn meta/v001
  [version
   db-spec
   base-url
   entity-uri-prefix
   entity-uri
   new-user-id
   new-user]
  (userresutils/user-out-transform new-user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Next user account id function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod next-user-account-id-fn meta/v001
  [version db-spec]
  (usercore/next-user-account-id db-spec))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Save new user function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod save-new-user-fn meta/v001
  [version
   db-spec
   _ ; plain text auth-token (not relevant; always null)
   new-user-id
   user]
  (usercore/save-new-user db-spec new-user-id user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Send email verification function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod send-email-verification-fn meta/v001
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
                                     flagged-url-maker-fn))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Make session function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod make-session-fn meta/v001
  [version db-spec user-entid]
  (let [new-token-id (usercore/next-auth-token-id db-spec)]
    (usercore/create-and-save-auth-token db-spec user-entid new-token-id)))
