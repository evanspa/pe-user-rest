(ns pe-user-rest.resource.prepare-password-reset-res
  (:require [liberator.core :refer [defresource]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-rest-utils.macros :refer [defmulti-by-version]]
            [liberator.representation :refer [ring-response]]
            [clostache.parser :refer [render-resource]]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-rest.utils :as userresutils]
            [pe-user-core.core :as usercore]
            [pe-user-rest.resource.password-reset-util :as pwdresetutil]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-prepare-password-reset
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   prepare-password-reset-uri
   email
   password-reset-token
   password-reset-form-mustache-template
   password-reset-form-action
   password-reset-error-mustache-template
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  (letfn [(resp [body-str]
            (ring-response {:headers {"content-type" "text/html"}
                            :status 200
                            :body body-str}))]
    (try
      (let [user (usercore/prepare-password-reset db-spec email password-reset-token)]
        (if (not (nil? user))
          (resp (render-resource password-reset-form-mustache-template
                                 (merge user
                                        {:password-reset-form-action password-reset-form-action
                                         (keyword pwdresetutil/param-new-password) pwdresetutil/param-new-password})))
          (resp (render-resource password-reset-error-mustache-template {}))))
      (catch Exception e
        (log/error e (str "Exception in handle-prepare-password-reset. (email: "
                          email
                          ", password-reset-form-mustache-template: "
                          password-reset-form-mustache-template
                          ", password-reset-form-action: "
                          password-reset-form-action
                          ", password-reset-error-mustache-template: "
                          password-reset-error-mustache-template ")"))
        (usercore/send-email err-notification-mustache-template
                             {:exception e
                              :params [email
                                       password-reset-form-mustache-template
                                       password-reset-form-action
                                       password-reset-error-mustache-template]}
                             err-subject
                             err-from-email
                             err-to-email)
        (resp (render-resource password-reset-error-mustache-template {}))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource prepare-password-reset-res
  [db-spec
   base-url
   entity-uri-prefix
   email
   password-reset-token
   password-reset-form-mustache-template
   password-reset-form-action
   password-reset-error-mustache-template
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  :available-media-types ["text/html"]
  :allowed-methods [:get]
  :handle-ok (fn [ctx]
               (handle-prepare-password-reset ctx
                                              db-spec
                                              base-url
                                              entity-uri-prefix
                                              (:uri (:request ctx))
                                              email
                                              password-reset-token
                                              password-reset-form-mustache-template
                                              password-reset-form-action
                                              password-reset-error-mustache-template
                                              err-notification-mustache-template
                                              err-subject
                                              err-from-email
                                              err-to-email)))
