(ns pe-user-rest.resource.send-password-reset-email-res
  (:require [liberator.core :refer [defresource]]
            [liberator.representation :refer [ring-response]]
            [clj-time.core :as t]
            [clojure.edn :as edn]
            [clojure.data.json :as json]
            [clojure.walk :refer [keywordize-keys]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-rest-utils.macros :refer [defmulti-by-version]]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-core.core :as usercore]
            [pe-user-rest.utils :as userresutils]
            [pe-user-core.validation :as userval]))

(declare body-data-in-transform-fn)
(declare do-send-password-reset-email-fn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-send-password-reset-email
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   entity-uri
   password-reset-email-mustache-template
   password-reset-email-subject-line
   password-reset-email-from
   password-reset-url-maker-fn
   password-reset-flagged-url-maker-fn
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  (rucore/put-or-post-invoker ctx
                              :post-as-do
                              db-spec
                              base-url
                              entity-uri-prefix
                              entity-uri
                              nil ; embedded-resources-fn
                              nil ; links-fn
                              []
                              nil ; plaintext-auth-token
                              nil ; user-validator-fn
                              nil ; any-issues-bit
                              body-data-in-transform-fn
                              nil ; body-data-out-transform-fn
                              nil ; next-entity-id-fn
                              nil ; save-new-entity-fn
                              nil ; save-entity-fn
                              nil ; hdr-establish-session
                              nil ; make-session-fn
                              (fn [version
                                   db-spec
                                   base-url
                                   entity-uri-prefix
                                   send-password-reset-email-uri
                                   plaintext-auth-token ; not used
                                   send-password-reset-email-post-as-do-body
                                   merge-embedded-fn
                                   merge-links-fn]
                                (do-send-password-reset-email-fn version
                                                                 db-spec
                                                                 send-password-reset-email-post-as-do-body
                                                                 password-reset-email-mustache-template
                                                                 password-reset-email-subject-line
                                                                 password-reset-email-from
                                                                 password-reset-url-maker-fn
                                                                 password-reset-flagged-url-maker-fn))
                              nil ; if-unmodified-since-hdr
                              (fn [exc-and-params]
                                (usercore/send-email err-notification-mustache-template
                                                     exc-and-params
                                                     err-subject
                                                     err-from-email
                                                     err-to-email))
                              #(identity %)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version body-data-in-transform-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; send password-reset email function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version do-send-password-reset-email-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource send-password-reset-email-res
  [db-spec
   mt-subtype-prefix
   hdr-error-mask
   base-url
   entity-uri-prefix
   password-reset-email-mustache-template
   password-reset-email-subject-line
   password-reset-email-from
   password-reset-url-maker-fn
   password-reset-flagged-url-maker-fn
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  :available-media-types (rucore/enumerate-media-types (meta/supported-media-types mt-subtype-prefix))
  :available-charsets rumeta/supported-char-sets
  :available-languages rumeta/supported-languages
  :allowed-methods [:post]
  :known-content-type? (rucore/known-content-type-predicate (meta/supported-media-types mt-subtype-prefix))
  :post! (fn [ctx] (handle-send-password-reset-email ctx
                                                     db-spec
                                                     base-url
                                                     entity-uri-prefix
                                                     (:uri (:request ctx))
                                                     password-reset-email-mustache-template
                                                     password-reset-email-subject-line
                                                     password-reset-email-from
                                                     password-reset-url-maker-fn
                                                     password-reset-flagged-url-maker-fn
                                                     err-notification-mustache-template
                                                     err-subject
                                                     err-from-email
                                                     err-to-email))
  :handle-created (fn [ctx] (rucore/handle-resp ctx nil hdr-error-mask)))
