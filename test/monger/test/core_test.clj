(ns monger.test.core-test
  (:require [monger core collection util result]
            [monger.test.helper :as helper]            
            [monger.collection :as mc])
  (:import [com.mongodb Mongo DB WriteConcern MongoOptions ServerAddress])
  (:use clojure.test
        [monger.core :only [server-address mongo-options]]))

(println (str "Using Clojure version " *clojure-version*))
(helper/connect!)

(deftest connect-to-mongo-with-default-host-and-port
  (let [connection (monger.core/connect)]
    (is (instance? com.mongodb.Mongo connection))))

(deftest connect-and-disconnect
  (monger.core/connect!)
  (monger.core/disconnect!)
  (monger.core/connect!))

(deftest connect-to-mongo-with-default-host-and-explicit-port
  (let [connection (monger.core/connect { :port 27017 })]
    (is (instance? com.mongodb.Mongo connection))))


(deftest connect-to-mongo-with-default-port-and-explicit-host
  (let [connection (monger.core/connect { :host "127.0.0.1" })]
    (is (instance? com.mongodb.Mongo connection))))

(when-not (System/getenv "CI")
  (deftest connect-to-mongo-via-uri-without-credentials
    (let [connection (monger.core/connect-via-uri! "mongodb://127.0.0.1/monger-test4")]
      (is (= (-> connection .getAddress ^InetAddress (.sameHost "127.0.0.1")))))
    ;; reconnect using regular host
    (helper/connect!))

  (deftest connect-to-mongo-via-uri-with-valid-credentials
    (let [connection (monger.core/connect-via-uri! "mongodb://clojurewerkz/monger!:monger!@127.0.0.1/monger-test4")]
      (is (= "monger-test4" (.getName (monger.core/current-db))))
      (is (= (-> connection .getAddress ^InetAddress (.sameHost "127.0.0.1"))))
      (mc/remove "documents")
      ;; make sure that the database is selected
      ;; and operations get through.
      (mc/insert "documents" {:field "value"})
      (is (= 1 (mc/count "documents" {}))))
    ;; reconnect using regular host
    (helper/connect!)))

(if-let [uri (System/getenv "MONGOHQ_URL")]
  (deftest ^{:external true} connect-to-mongo-via-uri-with-valid-credentials
    (let [connection (monger.core/connect-via-uri! uri)]
      (is (= (-> connection .getAddress ^InetAddress (.sameHost "127.0.0.1")))))
    ;; reconnect using regular host
    (helper/connect!)))


(deftest connect-to-mongo-via-uri-with-invalid-credentials
  (is (thrown? IllegalArgumentException
               (monger.core/connect-via-uri! "mongodb://clojurewerkz/monger!:ahsidaysd78jahsdi8@127.0.0.1/monger-test4"))))


(deftest test-mongo-options-builder
  (let [max-wait-time        (* 1000 60 2)
        ^MongoOptions result (monger.core/mongo-options :connections-per-host 3 :threads-allowed-to-block-for-connection-multiplier 50
                                                        :max-wait-time max-wait-time :connect-timeout 10 :socket-timeout 10 :socket-keep-alive true
                                                        :auto-connect-retry true :max-auto-connect-retry-time 0 :safe true
                                                        :w 1 :w-timeout 20 :fsync true :j true)]
    (is (= 3 (. result connectionsPerHost)))
    (is (= 50 (. result threadsAllowedToBlockForConnectionMultiplier)))
    (is (= max-wait-time (.maxWaitTime result)))
    (is (= 10 (.connectTimeout result)))
    (is (= 10 (.socketTimeout result)))
    (is (.socketKeepAlive result))
    (is (.autoConnectRetry result))
    (is (= 0 (.maxAutoConnectRetryTime result)))
    (is (.safe result))
    (is (= 1 (.w result)))
    (is (= 20 (.wtimeout result)))
    (is (.fsync result))
    (is (.j result))))

(deftest test-server-address
  (let [host              "127.0.0.1"
        port              7878
        ^ServerAddress sa (server-address host port)]
    (is (= host (.getHost sa)))
    (is (= port (.getPort sa)))))

(deftest use-existing-mongo-connection
  (let [^MongoOptions opts (mongo-options :threads-allowed-to-block-for-connection-multiplier 300)
        connection         (Mongo. "127.0.0.1" opts)]
    (monger.core/set-connection! connection)
    (is (= monger.core/*mongodb-connection* connection))))

(deftest connect-to-mongo-with-extra-options
  (let [^MongoOptions opts (mongo-options :threads-allowed-to-block-for-connection-multiplier 300)
        ^ServerAddress sa (server-address "127.0.0.1" 27017)]
    (monger.core/connect! sa opts)))


(deftest get-database
  (let [connection (monger.core/connect)
        db         (monger.core/get-db connection "monger-test")]
    (is (instance? com.mongodb.DB db))))


(deftest test-get-db-names
  (let [dbs (monger.core/get-db-names)]  
    (is (not (empty? dbs)))
    (is (dbs "monger-test"))))

(deftest get-last-error
  (let [connection (monger.core/connect)
        db         (monger.core/get-db connection "monger-test")]
    (is (monger.result/ok? (monger.core/get-last-error)))
    (is (monger.result/ok? (monger.core/get-last-error db)))
    (is (monger.result/ok? (monger.core/get-last-error db WriteConcern/NORMAL)))
    (is (monger.result/ok? (monger.core/get-last-error db 1 100 true)))))
