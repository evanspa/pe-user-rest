(ns pe-user-rest.meta
  (:require [pe-rest-utils.meta :as rumeta]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Media type vars
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn mt-subtype-user
  [mt-subtype-prefix]
  (str mt-subtype-prefix "user"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The versions of this REST API
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def v001 "0.0.1")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Link relations
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def logout-relation :logout)
(def send-verification-email-relation :send-verification-email)
(def send-password-reset-email-relation :send-password-reset-email)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; URL path components
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def pathcomp-users "users")
(def pathcomp-login "login")
(def pathcomp-light-login "light-login")
(def pathcomp-logout "logout")

(def pathcomp-send-verification-email "send-verification-email")
(def pathcomp-verification "verification")
(def pathcomp-verification-flagged "verification-flagged")

(def pathcomp-send-password-reset-email "send-password-reset-email")
(def pathcomp-prepare-password-reset "prepare-password-reset")
(def pathcomp-password-reset "password-reset")
(def pathcomp-password-reset-flagged "password-reset-flagged")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Information about this REST API, including supported content
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn supported-media-types
  [mt-subtype-prefix]
  "Convenient data structure that succinctly captures the set of media types
   (including version and format indicators) supported by this REST API."
  {rumeta/mt-type
   {:subtypes
    {(mt-subtype-user mt-subtype-prefix)
     {:versions {v001 {:format-inds #{"edn" "json"}}}}}}})
