(ns pe-user-rest.resource.user-test-utils
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.pprint :refer (pprint)]
            [clojure.tools.logging :as log]
            [compojure.core :refer [defroutes ANY]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [ring.mock.request :as mock]
            [clojure.java.jdbc :as j]
            [pe-user-rest.resource.login-res :as loginres]
            [pe-user-rest.resource.logout-res :as logoutres]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-user-core.ddl :as uddl]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-jdbc-utils.core :as jcore]
            [pe-user-rest.test-utils :refer [usermt-subtype-prefix
                                             user-auth-scheme
                                             user-auth-scheme-param-name
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-establish-session
                                             entity-uri-prefix
                                             login-uri-template
                                             light-login-uri-template
                                             logout-uri-template
                                             db-spec-without-db
                                             db-spec
                                             db-name]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn user-id-and-token-for-credentials
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (let [hdrs (:headers resp)
          resp-body-stream (:body resp)
          user-location-str (get hdrs "location")
          resp-user-id-str (rtucore/last-url-part user-location-str)
          auth-token (get hdrs userhdr-auth-token)]
      [resp-user-id-str auth-token])))

(defn assert-success-login
  [app credentials expected-embedded expected-links]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 200 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))
    (testing "headers and body of created user"
      (let [hdrs (:headers resp)
            resp-body-stream (:body resp)
            user-location-str (get hdrs "location")]
        (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
        (is (not (nil? resp-body-stream)))
        (let [resp-user-id-str (rtucore/last-url-part user-location-str)
              pct (rucore/parse-media-type (get hdrs "Content-Type"))
              charset (get rumeta/char-sets (:charset pct))
              resp-user (rucore/read-res pct resp-body-stream charset)
              embedded (get resp-user "_embedded")
              links (get resp-user "_links")
              auth-token (get hdrs userhdr-auth-token)]
          (is (= expected-embedded embedded))
          (is (= expected-links links))
          (is (not (nil? auth-token)))
          (is (not (nil? resp-user-id-str)))
          (is (not (nil? resp-user)))
          (is (not (nil? (get resp-user "user/created-at"))))
          (is (= "Karen Smith" (get resp-user "user/name")))
          (is (= "smithka@testing.com" (get resp-user "user/email")))
          (is (= "smithk" (get resp-user "user/username")))
          (is (nil? (get resp-user "user/password")))
          (let [[loaded-user-id loaded-user] (usercore/load-user-by-authtoken db-spec
                                                                              (Long/parseLong resp-user-id-str)
                                                                              auth-token)]
            (is (not (nil? loaded-user-id)))
            (is (= (Long/parseLong resp-user-id-str) loaded-user-id))))))))

(defn assert-success-light-login
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        light-login-uri-template)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 204 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))
    (testing "headers"
      (let [hdrs (:headers resp)
            resp-body-stream (:body resp)]
        (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
        (is (empty? resp-body-stream))
        (let [auth-token (get hdrs userhdr-auth-token)]
          (is (not (nil? auth-token))))))))

(defn assert-unauthorized-login
  ([app credentials reason reason-hdr]
   (assert-unauthorized-login app credentials reason reason-hdr login-uri-template))
  ([app credentials reason reason-hdr login-uri]
   (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                         (meta/mt-subtype-user usermt-subtype-prefix)
                                         meta/v001
                                         "UTF-8;q=1,ISO-8859-1;q=0"
                                         "json"
                                         "en-US"
                                         :post
                                         login-uri-template)
                 (mock/body (json/write-str credentials))
                 (mock/content-type (rucore/content-type rumeta/mt-type
                                                         (meta/mt-subtype-user usermt-subtype-prefix)
                                                         meta/v001
                                                         "json"
                                                         "UTF-8")))
         resp (app req)]
     (log/debug "assert-unauthorized-login resp: " resp)
     (testing "status code"
       (is (= 401 (:status resp))))
     (testing "login failure reason"
       (is (= reason (get (:headers resp) reason-hdr))))
     (testing "cookies"
       (is (= (empty? (:cookies resp))))))))

(defn assert-unauthorized-light-login
  [app credentials reason reason-hdr]
  (assert-unauthorized-login app credentials reason reason-hdr light-login-uri-template))

(defn assert-malformed-login
  [app credentials]
  (let [req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                        meta/v001
                                        "UTF-8;q=1,ISO-8859-1;q=0"
                                        "json"
                                        "en-US"
                                        :post
                                        login-uri-template)
                (mock/body (json/write-str credentials))
                (mock/content-type (rucore/content-type rumeta/mt-type
                                                        (meta/mt-subtype-user usermt-subtype-prefix)
                                                        meta/v001
                                                        "json"
                                                        "UTF-8")))
        resp (app req)]
    (testing "status code" (is (= 400 (:status resp))))
    (testing "cookies" (is (= (empty? (:cookies resp)))))))
