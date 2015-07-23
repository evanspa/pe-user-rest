(ns pe-user-rest.resource.version.logout-res-v001
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
            [pe-user-rest.resource.logout-res :refer [body-data-in-transform-fn
                                                      do-logout-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   db-spec
   _
   post-as-do-logout-input]
  (identity post-as-do-logout-input))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do logout function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-logout-fn meta/v001
  [version
   db-spec
   user-id
   base-url
   entity-uri-prefix
   logout-uri
   plaintext-auth-token
   logout-body
   merge-embedded-fn
   merge-links-fn]
  (usercore/invalidate-user-token db-spec
                                  user-id
                                  plaintext-auth-token
                                  usercore/invalrsn-logout)
  {:status 204})
