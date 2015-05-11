(ns pe-user-rest.resource.users-res-test
  (:require [clojure.test :refer :all]
            [clojure.data.json :as json]
            [clojure.pprint :refer (pprint)]
            [datomic.api :refer [q db] :as d]
            [compojure.core :refer [defroutes ANY]]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [compojure.handler :as handler]
            [clojure.tools.logging :as log]
            [ring.mock.request :as mock]
            [pe-datomic-utils.core :as ducore]
            [pe-apptxn-core.core :as apptxncore]
            [pe-user-rest.resource.users-res :as userres]
            [pe-user-rest.resource.version.users-res-v001]
            [pe-apptxn-restsupport.version.resource-support-v001]
            [pe-user-rest.meta :as meta]
            [pe-user-core.core :as usercore]
            [pe-datomic-testutils.core :as dtucore]
            [pe-rest-testutils.core :as rtucore]
            [pe-core-utils.core :as ucore]
            [pe-rest-utils.core :as rucore]
            [pe-rest-utils.meta :as rumeta]
            [pe-user-rest.apptxn :as userapptxn]
            [pe-user-rest.test-utils :refer [db-uri
                                             user-partition
                                             usermt-subtype-prefix
                                             user-schema-filename
                                             apptxn-logging-schema-filename
                                             base-url
                                             userhdr-auth-token
                                             userhdr-error-mask
                                             userhdr-apptxn-id
                                             userhdr-useragent-device-make
                                             userhdr-useragent-device-os
                                             userhdr-useragent-device-os-version
                                             userhdr-establish-session
                                             entity-uri-prefix
                                             users-uri-template]]))
(def conn (atom nil))

(defn embedded-resources-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   conn
   accept-format-ind
   user-entid]
  {})

(defn links-fn
  [version
   base-url
   entity-uri-prefix
   entity-uri
   user-entid]
  {})

(defroutes routes
  (ANY users-uri-template
       []
       (userres/users-res @conn
                          user-partition
                          user-partition
                          usermt-subtype-prefix
                          userhdr-auth-token
                          userhdr-error-mask
                          base-url
                          entity-uri-prefix
                          userhdr-apptxn-id
                          userhdr-useragent-device-make
                          userhdr-useragent-device-os
                          userhdr-useragent-device-os-version
                          userhdr-establish-session
                          embedded-resources-fn
                          links-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Middleware-decorated app
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def app
  (-> routes
      (handler/api)
      (wrap-cookies)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(use-fixtures :each (dtucore/make-db-refresher-fixture-fn db-uri
                                                          conn
                                                          user-partition
                                                          [user-schema-filename
                                                           apptxn-logging-schema-filename]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; The Tests
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(deftest success-user-creation-with-non-nil-req
  (testing "Successful creation of user with app txn logs."
    (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username @conn "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
                "user/username" "smithk"
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template
                                          userhdr-apptxn-id
                                          userhdr-useragent-device-make
                                          userhdr-useragent-device-os
                                          userhdr-useragent-device-os-version)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "last-modified"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (= "smithka@testing.com" (get resp-user "user/email")))
            (is (= "smithk" (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken @conn
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))
            (let [apptxns (apptxncore/all-apptxns @conn)
                  _ (is (= 1 (count apptxns)))
                  apptxn (first apptxns)
                  apptxnlogs (:apptxn/logs apptxn)]
              (is (= 0 (:apptxn/usecase apptxn)))
              (is (= "iPhone" (:apptxn/user-agent-device-make apptxn)))
              (is (= "iOS" (:apptxn/user-agent-device-os apptxn)))
              (is (= "8.1.2" (:apptxn/user-agent-device-os-version apptxn)))
              (is (= 2 (count apptxnlogs)))
              (is (= 3 (:apptxnlog/usecase-event (first apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (first apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (first apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (first apptxnlogs))))
              (is (= 5 (:apptxnlog/usecase-event (second apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (second apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (second apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (second apptxnlogs)))))))))))

(deftest success-user-creation-with-nil-username-req
  (testing "Successful creation of user with app txn logs."
    (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username @conn "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/email" "smithka@testing.com"
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template
                                          userhdr-apptxn-id
                                          userhdr-useragent-device-make
                                          userhdr-useragent-device-os
                                          userhdr-useragent-device-os-version)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "last-modified"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (= "smithka@testing.com" (get resp-user "user/email")))
            (is (nil? (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken @conn
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))
            (let [apptxns (apptxncore/all-apptxns @conn)
                  _ (is (= 1 (count apptxns)))
                  apptxn (first apptxns)
                  apptxnlogs (:apptxn/logs apptxn)]
              (is (= 0 (:apptxn/usecase apptxn)))
              (is (= "iPhone" (:apptxn/user-agent-device-make apptxn)))
              (is (= "iOS" (:apptxn/user-agent-device-os apptxn)))
              (is (= "8.1.2" (:apptxn/user-agent-device-os-version apptxn)))
              (is (= 2 (count apptxnlogs)))
              (is (= 3 (:apptxnlog/usecase-event (first apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (first apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (first apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (first apptxnlogs))))
              (is (= 5 (:apptxnlog/usecase-event (second apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (second apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (second apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (second apptxnlogs)))))))))))

(deftest success-user-creation-with-nil-email-req
  (testing "Successful creation of user with app txn logs."
    (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username @conn "smithk")))
    (let [user {"user/name" "Karen Smith"
                "user/username" "smithk"
                "user/password" "insecure"}
          req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                          meta/v001
                                          "UTF-8;q=1,ISO-8859-1;q=0"
                                          "json"
                                          "en-US"
                                          :post
                                          users-uri-template
                                          userhdr-apptxn-id
                                          userhdr-useragent-device-make
                                          userhdr-useragent-device-os
                                          userhdr-useragent-device-os-version)
                  (rtucore/header userhdr-establish-session "true")
                  (mock/body (json/write-str user))
                  (mock/content-type (rucore/content-type rumeta/mt-type
                                                          (meta/mt-subtype-user usermt-subtype-prefix)
                                                          meta/v001
                                                          "json"
                                                          "UTF-8")))
          resp (app req)]
      (testing "status code" (is (= 201 (:status resp))))
      (testing "cookies" (is (= (empty? (:cookies resp)))))
      (testing "headers and body of created user"
        (let [hdrs (:headers resp)
              resp-body-stream (:body resp)
              user-location-str (get hdrs "location")]
          (is (= "Accept, Accept-Charset, Accept-Language" (get hdrs "Vary")))
          (is (not (nil? resp-body-stream)))
          (is (not (nil? user-location-str)))
          (let [resp-user-entid-str (rtucore/last-url-part user-location-str)
                pct (rucore/parse-media-type (get hdrs "Content-Type"))
                charset (get rumeta/char-sets (:charset pct))
                resp-user (rucore/read-res pct resp-body-stream charset)
                auth-token (get hdrs userhdr-auth-token)]
            (is (not (nil? auth-token)))
            (is (not (nil? resp-user-entid-str)))
            (is (not (nil? resp-user)))
            (is (not (nil? (get resp-user "last-modified"))))
            (is (= "Karen Smith" (get resp-user "user/name")))
            (is (nil? (get resp-user "user/email")))
            (is (= "smithk" (get resp-user "user/username")))
            (is (nil? (get resp-user "user/hashed-password")))
            (let [[loaded-user-entid loaded-user-ent] (usercore/load-user-by-authtoken @conn
                                                                                       (Long/parseLong resp-user-entid-str)
                                                                                       auth-token)]
              (is (not (nil? loaded-user-entid)))
              (is (= (Long/parseLong resp-user-entid-str) loaded-user-entid)))
            (let [apptxns (apptxncore/all-apptxns @conn)
                  _ (is (= 1 (count apptxns)))
                  apptxn (first apptxns)
                  apptxnlogs (:apptxn/logs apptxn)]
              (is (= 0 (:apptxn/usecase apptxn)))
              (is (= "iPhone" (:apptxn/user-agent-device-make apptxn)))
              (is (= "iOS" (:apptxn/user-agent-device-os apptxn)))
              (is (= "8.1.2" (:apptxn/user-agent-device-os-version apptxn)))
              (is (= 2 (count apptxnlogs)))
              (is (= 3 (:apptxnlog/usecase-event (first apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (first apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (first apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (first apptxnlogs))))
              (is (= 5 (:apptxnlog/usecase-event (second apptxnlogs))))
              (is (not (nil? (:apptxnlog/timestamp (second apptxnlogs)))))
              (is (nil? (:apptxnlog/edn-ctx (second apptxnlogs))))
              (is (nil? (:apptxnlog/in-ctx-err-desc (second apptxnlogs)))))))))))

(deftest failed-user-creation
  (testing "Unsuccessful creation of user with app txn logs."
    (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username @conn "smithk")))
    (with-redefs [usercore/load-user-by-email (fn [conn email] (throw (Exception. "exception")))]
      (let [user {"user/name" "Karen Smith"
                  "user/email" "smithka@testing.com"
                  "user/username" "smithk"
                  "user/password" "insecure"}
            req (-> (rtucore/req-w-std-hdrs rumeta/mt-type
                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                            meta/v001
                                            "UTF-8;q=1,ISO-8859-1;q=0"
                                            "json"
                                            "en-US"
                                            :post
                                            users-uri-template
                                            userhdr-apptxn-id
                                            userhdr-useragent-device-make
                                            userhdr-useragent-device-os
                                            userhdr-useragent-device-os-version)
                    (rtucore/header userhdr-establish-session "true")
                    (mock/body (json/write-str user))
                    (mock/content-type (rucore/content-type rumeta/mt-type
                                                            (meta/mt-subtype-user usermt-subtype-prefix)
                                                            meta/v001
                                                            "json"
                                                            "UTF-8")))
            resp (app req)]
        (testing "status code" (is (= 500 (:status resp))))
        (let [apptxns (apptxncore/all-apptxns @conn)
              _ (is (= 1 (count apptxns)))
              apptxn (first apptxns)
              apptxnlogs (:apptxn/logs apptxn)]
          (is (= 0 (:apptxn/usecase apptxn)))
          (is (= "iPhone" (:apptxn/user-agent-device-make apptxn)))
          (is (= "iOS" (:apptxn/user-agent-device-os apptxn)))
          (is (= "8.1.2" (:apptxn/user-agent-device-os-version apptxn)))
          (is (= 2 (count apptxnlogs)))
          (is (= 3 (:apptxnlog/usecase-event (first apptxnlogs))))
          (is (not (nil? (:apptxnlog/timestamp (first apptxnlogs)))))
          (is (= 4 (:apptxnlog/usecase-event (second apptxnlogs))))
          (is (not (nil? (:apptxnlog/timestamp (second apptxnlogs)))))
          (is (not (nil? (:apptxnlog/edn-ctx (second apptxnlogs)))))
          (is (not (nil? (:apptxnlog/in-ctx-err-desc (second apptxnlogs))))))))
    (is (nil? (usercore/load-user-by-email @conn "smithka@testing.com")))
    (is (nil? (usercore/load-user-by-username @conn "smithk")))))
