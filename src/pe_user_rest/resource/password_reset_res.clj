(ns pe-user-rest.resource.password-reset-res
  (:require [liberator.core :refer [defresource]]
            [liberator.representation :refer [ring-response]]
            [clj-time.core :as t]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [clostache.parser :refer [render-resource]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-rest.utils :as userresutils]
            [pe-user-core.validation :as userval]
            [pe-user-rest.resource.password-reset-util :as pwdresetutil]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-password-reset!
  [ctx
   db-spec
   email
   password-reset-token]
  (let [params (get-in ctx [:request :params])
        new-password (get params (keyword pwdresetutil/param-new-password))]
    (try
      (usercore/reset-password db-spec email password-reset-token new-password)
      [true {:password-reset-result true}]
      (catch Exception e
        (log/error e "Exception caught")
        [true {:password-reset-result false}]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource password-reset-res
  [db-spec
   base-url
   entity-uri-prefix
   email
   password-reset-token
   password-reset-success-mustache-template
   password-reset-error-mustache-template]
  :available-media-types ["text/html"]
  :allowed-methods [:post]
  :post! (fn [ctx] (handle-password-reset! ctx db-spec email password-reset-token))
  :new? false
  :respond-with-entity? true
  :multiple-representations? false
  :handle-ok (fn [ctx]
                    (letfn [(resp [body-str]
                              (ring-response {:headers {"content-type" "text/html"}
                                              :status 200
                                              :body body-str}))]
                      (if (:password-reset-result ctx)
                        (resp (render-resource password-reset-success-mustache-template {}))
                        (resp (render-resource password-reset-error-mustache-template {}))))))
