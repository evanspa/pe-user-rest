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
            [ring.util.response :refer [redirect]]
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
   password-reset-web-url
   password-reset-error-web-url
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  (try
    (let [user (usercore/prepare-password-reset db-spec email password-reset-token)]
      (if (not (nil? user))
        (ring-response (redirect password-reset-web-url))
        (ring-response (redirect password-reset-error-web-url))))
    (catch IllegalArgumentException e
      (ring-response
       (redirect (str password-reset-error-web-url "/" (.getMessage e)))))
    (catch Exception e
      (log/error e (str "Exception in handle-prepare-password-reset. (email: "
                        email
                        ", password-reset-web-url: "
                        password-reset-web-url
                        ", password-reset-error-web-url: "
                        password-reset-error-web-url ")"))
      (usercore/send-email err-notification-mustache-template
                           {:exception e}
                           err-subject
                           err-from-email
                           err-to-email)
      (ring-response (redirect password-reset-error-web-url)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource prepare-password-reset-res
  [db-spec
   base-url
   entity-uri-prefix
   email
   password-reset-token
   password-reset-web-url
   password-reset-error-web-url
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
                                              password-reset-web-url
                                              password-reset-error-web-url
                                              err-notification-mustache-template
                                              err-subject
                                              err-from-email
                                              err-to-email)))
