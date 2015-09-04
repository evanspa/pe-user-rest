(ns pe-user-rest.utils
  (:require [pe-rest-utils.core :as rucore]
            [pe-user-rest.meta :as meta]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [pe-core-utils.core :as ucore]
            [pe-user-core.core :as usercore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn get-plaintext-auth-token
  [ctx scheme scheme-param-name]
  (let [authorization (get-in ctx [:request :headers "authorization"])
        [_
         _
         auth-scheme-param-value] (rucore/parse-auth-header authorization
                                                            scheme
                                                            scheme-param-name)]
    auth-scheme-param-value))

(defn become-unauthenticated
  ([db-spec user-id plaintext-auth-token]
   (become-unauthenticated db-spec user-id plaintext-auth-token nil))
  ([db-spec user-id plaintext-auth-token reason]
   (do
     (usercore/invalidate-user-token db-spec user-id plaintext-auth-token reason)
     (throw (ex-info nil {:cause :became-unauthenticated})))))

(defn authorized?
  [ctx conn user-entid scheme scheme-param-name]
  (let [authorization (get-in ctx [:request :headers "authorization"])]
    (when-let [[auth-scheme
                auth-scheme-param-name
                auth-scheme-param-value] (rucore/parse-auth-header authorization
                                                                   scheme
                                                                   scheme-param-name)]
      (let [[found-user-entid
             found-user-ent] (usercore/authenticate-user-by-authtoken conn
                                                                      user-entid
                                                                      auth-scheme-param-value)]
        (and (not (nil? found-user-ent))
             (= found-user-entid user-entid))))))

(defn make-user-subentity-url
  [base-url entity-uri-prefix user-id pathcomp-subent sub-id]
  (rucore/make-abs-link-href base-url
                             (str entity-uri-prefix
                                  meta/pathcomp-users
                                  "/"
                                  user-id
                                  "/"
                                  pathcomp-subent
                                  "/"
                                  sub-id)))

(defn user-out-transform
  [user]
  (-> user
      (dissoc :user/password)
      (dissoc :user/hashed-password)
      (ucore/transform-map-val :user/created-at #(c/to-long %))
      (ucore/transform-map-val :user/deleted-at #(c/to-long %))
      (ucore/transform-map-val :user/suspended-at #(c/to-long %))
      (ucore/transform-map-val :user/updated-at #(c/to-long %))
      (ucore/transform-map-val :user/verified-at #(c/to-long %))))
