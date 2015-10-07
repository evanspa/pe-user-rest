(ns pe-user-rest.resource.account-verification-res
  (:require [liberator.core :refer [defresource]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-rest-utils.macros :refer [defmulti-by-version]]
            [liberator.representation :refer [ring-response]]
            [clostache.parser :refer [render-resource]]
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
   email
   verification-token
   verification-success-mustache-template
   verification-error-mustache-template]
  (letfn [(resp [body-str]
            (ring-response {:headers {"content-type" "text/html"}
                            :status 200
                            :body body-str}))]
    (try
      (let [user (usercore/verify-user db-spec email verification-token)]
        (if (not (nil? user))
          (resp (render-resource verification-success-mustache-template user))
          (resp (render-resource verification-error-mustache-template {}))))
      (catch Exception e
        (resp (render-resource verification-error-mustache-template {}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource account-verification-res
  [db-spec
   base-url
   entity-uri-prefix
   email
   verification-token
   verification-success-mustache-template
   verification-error-mustache-template]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx]
               (verify-user ctx
                            db-spec
                            base-url
                            entity-uri-prefix
                            (:uri (:request ctx))
                            email
                            verification-token
                            verification-success-mustache-template
                            verification-error-mustache-template)))
