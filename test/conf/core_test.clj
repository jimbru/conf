(ns clams.test.conf-test
  (:require [clojure.test :refer :all]
            [clams.conf :as conf]))

(def mock-conf-base-edn
  "{
     :port 5000
   }")

(def mock-conf-default-edn
  "{
     :database-url \"sql://fake:1234/foobar\"
     :log-level :debug ;; verbose!
   }")

(def mock-conf-dev-edn
  "{
     :database-url \"sql://fake:1234/devdb\"
   }")

(def mock-conf-prod-edn
  "{
     :database-url \"sql://fake:1234/proddb\"
   }")

(defn stub-slurp
  [file]
  (condp = file
    "conf/prod.edn"    mock-conf-prod-edn
    "conf/dev.edn"     mock-conf-dev-edn
    "conf/default.edn" mock-conf-default-edn
    "conf/base.edn"    mock-conf-base-edn
    (throw (Exception. "Unknown fixture."))))

(defn wrap-fixtures
  [props env f]
  (with-redefs [clojure.java.io/resource     identity
                clojure.core/slurp           stub-slurp
                clams.conf/sys-getenv        (fn [] env)
                clams.conf/sys-getproperties (fn [] props)]
    (f)))

(use-fixtures :each (fn [f]
  (f)
  (conf/unload!)))

(deftest file-not-found-test
  (with-redefs [clojure.java.io/resource (fn [_] nil)]
    (conf/load!)
    (is (= (conf/get :log-level) nil))))

(deftest get-from-base-test
  (conf/load!)
  (is (= (conf/get :port) 5000)))

(deftest get-from-default-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :database-url) "sql://fake:1234/foobar"))
  (is (= (conf/get :log-level) :debug)))

(deftest get-from-clams-env-test
  (testing "dev"
    (wrap-fixtures {} {"CLAMS_ENV" "dev"} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/devdb"))
    (conf/unload!))
  (testing "prod"
    (wrap-fixtures {} {"clams.env" "PrOd"} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/proddb"))
    (conf/unload!))
  (testing "using system property"
    (wrap-fixtures {"clams.env" "dev"} {} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/devdb"))
    (conf/unload!)))

(deftest get-from-env-test
  (wrap-fixtures {} {"DATABASE_URL" "sql://dev.fake:1234/foobar"} conf/load!)
  (is (= (conf/get :database-url) "sql://dev.fake:1234/foobar"))
  (is (= (conf/get-all) {:database-url "sql://dev.fake:1234/foobar"
                         :log-level    :debug
                         :port         5000})))

(deftest get-from-props-test
  (wrap-fixtures {"database.url" "sql://props:1234"}
                 {"DATABASE_URL" "sql://dev.fake:1234/foobar"}
                 conf/load!)
  (is (= (conf/get :database-url) "sql://props:1234")))

(deftest get-not-found-arg-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :xxx) nil))
  (is (= (conf/get :xxx :foobar) :foobar)))

(deftest get-all-test
  (wrap-fixtures {"foo" "bar"} {"baz" "quux"} conf/load!)
  (is (= (conf/get-all)
         {:database-url "sql://fake:1234/foobar"
          :log-level    :debug
          :port         5000
          :foo          "bar"
          :baz          "quux"})))

(deftest set!-test
  (is (= (conf/get :port) 5000))
  (is (= (conf/get :color) nil))
  (conf/set! :color "red")
  (conf/set! :port 5001)
  (is (= (conf/get :port) 5001))
  (is (= (conf/get :color) "red")))
