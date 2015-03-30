(ns pe-user-rest.resource.version.login-res-v001
  (:require [datomic.api :refer [q db] :as d]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-core-utils.core :as ucore]
            [pe-datomic-utils.core :as ducore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]
            [pe-user-rest.apptxn :as userapptxn]
            [pe-user-rest.meta :as meta]
            [pe-user-rest.resource.login-res :refer [body-data-in-transform-fn
                                                     body-data-out-transform-fn
                                                     do-login-fn]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod body-data-in-transform-fn meta/v001
  [version body-data]
  (identity body-data))

(defmethod body-data-out-transform-fn meta/v001
  [version body-data]
  (identity body-data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 0.0.1 do login function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmethod do-login-fn meta/v001
  [version
   conn
   partition
   base-url
   entity-uri-prefix
   login-uri
   body-data
   apptxnlogger
   merge-embedded-fn
   merge-links-fn]
  (let [{username-or-email :user/username-or-email
         password :user/password} body-data]
    (if (and (not (nil? username-or-email))
             (not (nil? password)))
      (let [[user-entid user-ent] (usercore/authenticate-user-by-password conn username-or-email password)]
        (if (not (nil? user-ent))
          (let [user (-> (into {} user-ent)
                         (ucore/trim-keys [:user/auth-token :user/hashed-password])
                         (merge-links-fn user-entid)
                         (merge-embedded-fn user-entid))
                [token newauthtoken-txnmap] (usercore/create-and-save-auth-token-txnmap partition user-entid nil)
                user-txn-time (ducore/txn-time conn user-entid :user/hashed-password)
                user-txn-time-str (ucore/instant->rfc7231str user-txn-time)]
            @(d/transact conn [newauthtoken-txnmap])
            (apptxnlogger userapptxn/apptxnlog-login-remote-proc-done-success)
            {:status 200
             :do-entity user
             :location (rucore/make-abs-link-href base-url
                                                  (format "%s%s/%s"
                                                          entity-uri-prefix
                                                          meta/pathcomp-users
                                                          user-entid))
             :last-modified user-txn-time-str
             :auth-token token})
          (do
            (apptxnlogger userapptxn/apptxnlog-login-remote-proc-done-invalid)
            {:status 401})))
      (do
        (apptxnlogger userapptxn/apptxnlog-login-remote-proc-done-invalid)
        {:status 400}))))
