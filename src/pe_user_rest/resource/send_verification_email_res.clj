(ns pe-user-rest.resource.send-verification-email-res
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
(declare do-send-verification-email-fn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-send-verification-email
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   entity-uri
   user-id
   plaintext-auth-token
   verification-email-mustache-template
   verification-email-subject-line
   verification-email-from
   verification-url-maker-fn
   verification-flagged-url-maker-fn
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
                              [user-id]
                              plaintext-auth-token
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
                                   user-id
                                   base-url
                                   entity-uri-prefix
                                   send-verification-email-uri
                                   plaintext-auth-token
                                   send-verification-email-post-as-do-body
                                   merge-embedded-fn
                                   merge-links-fn]
                                (do-send-verification-email-fn version
                                                               db-spec
                                                               user-id
                                                               verification-email-mustache-template
                                                               verification-email-subject-line
                                                               verification-email-from
                                                               verification-url-maker-fn
                                                               verification-flagged-url-maker-fn))
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
;; send verification email function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version do-send-verification-email-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resources Definitions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource send-verification-email-res
  [db-spec
   mt-subtype-prefix
   hdr-auth-token
   hdr-error-mask
   auth-scheme
   auth-scheme-param-name
   base-url
   entity-uri-prefix
   user-id
   verification-email-mustache-template
   verification-email-subject-line
   verification-email-from
   verification-url-maker-fn
   verification-flagged-url-maker-fn
   err-notification-mustache-template
   err-subject
   err-from-email
   err-to-email]
  :available-media-types (rucore/enumerate-media-types (meta/supported-media-types mt-subtype-prefix))
  :available-charsets rumeta/supported-char-sets
  :available-languages rumeta/supported-languages
  :allowed-methods [:post]
  :authorized? (fn [ctx] (userresutils/authorized? ctx
                                                   db-spec
                                                   user-id
                                                   auth-scheme
                                                   auth-scheme-param-name))
  :known-content-type? (rucore/known-content-type-predicate (meta/supported-media-types mt-subtype-prefix))
  :exists? (fn [ctx] (not (nil? (usercore/load-user-by-id db-spec user-id))))
  :post! (fn [ctx] (handle-send-verification-email ctx
                                                   db-spec
                                                   base-url
                                                   entity-uri-prefix
                                                   (:uri (:request ctx))
                                                   user-id
                                                   (userresutils/get-plaintext-auth-token ctx
                                                                                          auth-scheme
                                                                                          auth-scheme-param-name)
                                                   verification-email-mustache-template
                                                   verification-email-subject-line
                                                   verification-email-from
                                                   verification-url-maker-fn
                                                   verification-flagged-url-maker-fn
                                                   err-notification-mustache-template
                                                   err-subject
                                                   err-from-email
                                                   err-to-email))
  :handle-created (fn [ctx] (rucore/handle-resp ctx
                                                hdr-auth-token
                                                hdr-error-mask)))
