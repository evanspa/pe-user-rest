(ns pe-user-rest.resource.version.login-res-v001
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
            [pe-user-rest.utils :as userresutils]
            [pe-user-rest.resource.login-res :refer [body-data-in-transform-fn
                                                     body-data-out-transform-fn
                                                     do-login-fn
                                                     do-light-login-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-login-exception
  [e]
  (let [cause (-> e ex-data :cause)]
    (cond
      (= cause :account-is-suspended) {:status 401
                                       :login-failed-reason usercore/loginfailrsn-account-suspended}
      (= cause :account-is-deleted) {:status 401
                                     :login-failed-reason usercore/loginfailrsn-account-deleted}
      :else {:status 401})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version
   post-as-do-login-input]
  (identity post-as-do-login-input))

(defmethod body-data-out-transform-fn meta/v001
  [version
   db-spec
   base-url
   entity-uri-prefix
   login-uri
   post-as-do-login-result]
  (userresutils/user-out-transform post-as-do-login-result))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do login function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-login-fn meta/v001
  [version
   db-spec
   base-url
   entity-uri-prefix
   login-uri
   _ ; auth token (obviously not relevant here)
   body-data
   merge-embedded-fn
   merge-links-fn]
  (let [{username-or-email :user/username-or-email
         password :user/password} body-data]
    (if (and (not (nil? username-or-email))
             (not (nil? password)))
      (try
        (let [[user-id user] (usercore/authenticate-user-by-password db-spec username-or-email password)]
          (if (not (nil? user))
            (let [new-token-id (usercore/next-auth-token-id db-spec)
                  user (-> (into {:last-modified (c/to-long (:user/updated-at user))} user)
                           (ucore/trim-keys [:user/hashed-password])
                           (merge-links-fn user-id)
                           (merge-embedded-fn user-id db-spec))
                  plaintext-token (usercore/create-and-save-auth-token db-spec user-id new-token-id)]
              {:status 200
               :do-entity user
               :location (rucore/make-abs-link-href base-url
                                                    (format "%s%s/%s"
                                                            entity-uri-prefix
                                                            meta/pathcomp-users
                                                            user-id))
               :auth-token plaintext-token})
            {:status 401}))
        (catch clojure.lang.ExceptionInfo e
          (handle-login-exception e)))
      {:status 400})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do light login function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-light-login-fn meta/v001
  [version
   db-spec
   base-url
   entity-uri-prefix
   light-login-uri
   _ ; auth token (obviously not relevant here)
   body-data
   merge-embedded-fn
   merge-links-fn]
  (let [{username-or-email :user/username-or-email
         password :user/password} body-data]
    (if (and (not (nil? username-or-email))
             (not (nil? password)))
      (try
        (let [[user-id _] (usercore/authenticate-user-by-password db-spec username-or-email password)]
          (if (not (nil? user-id))
            (let [new-token-id (usercore/next-auth-token-id db-spec)
                  plaintext-token (usercore/create-and-save-auth-token db-spec user-id new-token-id)]
              {:status 204
               :location (rucore/make-abs-link-href base-url
                                                    (format "%s%s/%s"
                                                            entity-uri-prefix
                                                            meta/pathcomp-users
                                                            user-id))
               :auth-token plaintext-token})
            {:status 401}))
        (catch clojure.lang.ExceptionInfo e
          (handle-login-exception e)))
      {:status 400})))
