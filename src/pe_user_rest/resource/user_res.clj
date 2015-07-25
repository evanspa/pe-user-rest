(ns pe-user-rest.resource.user-res
  (:require [liberator.core :refer [defresource]]
            [pe-user-rest.meta :as meta]
            [clojure.tools.logging :as log]
            [pe-rest-utils.macros :refer [defmulti-by-version]]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-rest.utils :as userresutils]
            [pe-user-core.core :as usercore]
            [pe-user-core.validation :as userval]))

(declare save-user-validator-fn)
(declare body-data-in-transform-fn)
(declare body-data-out-transform-fn)
(declare save-user-fn)
(declare delete-user-fn)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn handle-user-put!
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   user-uri
   user-id
   plaintext-auth-token
   embedded-resources-fn
   links-fn
   if-unmodified-since-hdr]
  (rucore/put-or-post-invoker ctx
                              :put
                              db-spec
                              base-url
                              entity-uri-prefix
                              user-uri
                              embedded-resources-fn
                              links-fn
                              [user-id]
                              plaintext-auth-token
                              save-user-validator-fn
                              userval/su-any-issues
                              body-data-in-transform-fn
                              body-data-out-transform-fn
                              nil
                              nil
                              save-user-fn
                              nil
                              nil
                              nil
                              if-unmodified-since-hdr))

(defn handle-user-delete!
  [ctx
   db-spec
   base-url
   entity-uri-prefix
   envlog-uri
   user-id
   plaintext-auth-token
   embedded-resources-fn
   links-fn
   if-unmodified-since-hdr
   delete-reason-hdr]
  (rucore/delete-invoker ctx
                         db-spec
                         base-url
                         entity-uri-prefix
                         envlog-uri
                         embedded-resources-fn
                         links-fn
                         [user-id]
                         plaintext-auth-token
                         body-data-out-transform-fn
                         delete-user-fn
                         delete-reason-hdr
                         if-unmodified-since-hdr))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Validator function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version save-user-validator-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; body-data transformation functions
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version body-data-in-transform-fn meta/v001)
(defmulti-by-version body-data-out-transform-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Save user function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version save-user-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete user function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmulti-by-version delete-user-fn meta/v001)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Resource definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defresource user-res
  [db-spec
   mt-subtype-prefix
   hdr-auth-token
   hdr-error-mask
   auth-scheme
   auth-scheme-param-name
   base-url
   entity-uri-prefix
   user-id
   embedded-resources-fn
   links-fn
   if-unmodified-since-hdr
   delete-reason-hdr]
  :available-media-types (rucore/enumerate-media-types (meta/supported-media-types mt-subtype-prefix))
  :available-charsets rumeta/supported-char-sets
  :available-languages rumeta/supported-languages
  :allowed-methods [:put :delete]
  :authorized? (fn [ctx] (userresutils/authorized? ctx
                                                   db-spec
                                                   user-id
                                                   auth-scheme
                                                   auth-scheme-param-name))
  :known-content-type? (rucore/known-content-type-predicate (meta/supported-media-types mt-subtype-prefix))
  :exists? (fn [ctx] (not (nil? (usercore/load-user-by-id db-spec user-id))))
  :can-put-to-missing? false
  :new? false
  :respond-with-entity? true
  :multiple-representations? false
  :put! (fn [ctx] (handle-user-put! ctx
                                    db-spec
                                    base-url
                                    entity-uri-prefix
                                    (:uri (:request ctx))
                                    user-id
                                    (userresutils/get-plaintext-auth-token ctx
                                                                           auth-scheme
                                                                           auth-scheme-param-name)
                                    embedded-resources-fn
                                    links-fn
                                    if-unmodified-since-hdr))
  :delete! (fn [ctx] (handle-user-delete! ctx
                                          db-spec
                                          base-url
                                          entity-uri-prefix
                                          (:uri (:request ctx))
                                          user-id
                                          (userresutils/get-plaintext-auth-token ctx
                                                                                 auth-scheme
                                                                                 auth-scheme-param-name)
                                          embedded-resources-fn
                                          links-fn
                                          if-unmodified-since-hdr
                                          delete-reason-hdr))
  :handle-ok (fn [ctx] (rucore/handle-resp ctx
                                           hdr-auth-token
                                           hdr-error-mask)))
