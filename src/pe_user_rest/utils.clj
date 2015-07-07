(ns pe-user-rest.utils
  (:require [pe-rest-utils.core :as rucore]
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
