(ns pe-user-rest.resource.version.user-res-v001
  (:require [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [pe-core-utils.core :as ucore]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]
            [pe-user-rest.resource.user-res :refer [save-user-validator-fn
                                                    body-data-in-transform-fn
                                                    body-data-out-transform-fn
                                                    save-user-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Validator function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod save-user-validator-fn meta/v001
  [version user]
  (userval/save-user-validation-mask user))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   db-spec
   user-id
   user]
  (identity user))

(defmethod body-data-out-transform-fn meta/v001
  [version
   db-spec
   user-id
   user]
  (-> user
      (dissoc :user/password)
      (dissoc :user/hashed-password)
      (ucore/transform-map-val :user/created-at #(c/to-long %))
      (ucore/transform-map-val :user/updated-at #(c/to-long %))
      (ucore/transform-map-val :user/verified-at #(c/to-long %))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 Save user function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod save-user-fn meta/v001
  [version
   db-spec
   user-id
   plaintext-auth-token ; in case you want to invalidate it
   user
   if-unmodified-since]
  (usercore/save-user db-spec user-id plaintext-auth-token user if-unmodified-since))
