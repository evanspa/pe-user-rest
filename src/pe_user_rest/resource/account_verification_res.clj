(ns pe-user-rest.resource.account-verification-res
  (:require [liberator.core :refer [defresource]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-rest-utils.macros :refer [defmulti-by-version]]
            [liberator.representation :refer [ring-response]]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-rest.utils :as userresutils]
            [pe-user-core.core :as usercore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn verify-user
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   user-uri
   user-id
   verification-token]
  (try
    (usercore/verify-user db-spec user-id verification-token)
    (ring-response {:headers {"content-type" "text/html"}
                    :status 200
                    :body "<html><head><title>Verified!</title><body>verified.</body></html>"})
    (catch Exception e
      (ring-response {:headers {"content-type" "text/html"}
                      :status 200
                      :body "<html><head><title>Not Verified.</title><body>not verified.</body></html>"}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource account-verification-res
  [db-spec
   base-url
   entity-uri-prefix
   user-id
   verification-token]
  :available-media-types ["text/html"]
  :available-charsets rumeta/supported-char-sets
  :available-languages rumeta/supported-languages
  :allowed-methods [:get]
  :exists? (fn [ctx] (not (nil? (usercore/load-user-by-id db-spec user-id))))
  :handle-ok (fn [ctx]
               (verify-user ctx
                            db-spec
                            base-url
                            entity-uri-prefix
                            (:uri (:request ctx))
                            user-id
                            verification-token)))
