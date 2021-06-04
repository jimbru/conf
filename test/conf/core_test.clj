(ns conf.core-test
  (:require [clojure.test :refer :all]
            [conf.core :as conf]))

(def mock-conf-base-edn
  "{:port 5000
    }")

(def mock-conf-default-edn
  "{:database-url \"sql://fake:1234/foobar\"
    :bt-database-url #var :database-url
    :log-level :debug ; verbose!
    }")

(def mock-conf-dev-edn
  "{:database-url \"sql://fake:1234/devdb\"
    :xyz-database-url #var :bt-database-url
    }")

(def mock-conf-prod-default-edn
  "{:database-url \"sql://fake:1234/proddb_a\"
    :log-level :trace
    }")

(def mock-conf-prod-edn
  "{:database-url \"sql://fake:1234/proddb_b\"
    }")

(defn stub-slurp [file]
  (condp = file
    "conf/prod.edn"             mock-conf-prod-edn
    "conf/defaults/prod.edn"    mock-conf-prod-default-edn
    "conf/dev.edn"              mock-conf-dev-edn
    "conf/default.edn"          mock-conf-default-edn
    "conf/base.edn"             mock-conf-base-edn
    (if (re-find #"conf/defaults/\w+\.edn" file)
        "{}"
        (throw (Exception. "Unknown fixture.")))))

(defn wrap-fixtures [props env f]
  (with-redefs [clojure.java.io/resource    identity
                clojure.core/slurp          stub-slurp
                conf.core/sys-getenv        (fn [] env)
                conf.core/sys-getproperties (fn [] props)]
    (f)))

(use-fixtures :each (fn [f]
                      (f)
                      (conf/unload!)))

(deftest file-not-found-test
  (with-redefs [clojure.java.io/resource (fn [_] nil)]
    (conf/load!)
    (is (= (conf/get :log-level) nil))))

(deftest get-from-base-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :port) 5000)))

(deftest get-from-default-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :database-url) "sql://fake:1234/foobar"))
  (is (= (conf/get :log-level) :debug)))

(deftest get-from-conf-env-test
  (testing "dev"
    (wrap-fixtures {} {"CONF_ENV" "dev"} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/devdb"))
    (conf/unload!))
  (testing "prod"
    (wrap-fixtures {} {"conf.env" "PrOd"} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/proddb_b"))
    (is (= (conf/get :log-level) :trace))
    (conf/unload!))
  (testing "using system property"
    (wrap-fixtures {"conf.env" "dev"} {} conf/load!)
    (is (= (conf/get :database-url) "sql://fake:1234/devdb"))
    (conf/unload!)))

(deftest get-from-env-test
  (wrap-fixtures {} {"DATABASE_URL" "sql://dev.fake:1234/foobar"} conf/load!)
  (is (= (conf/get :database-url) "sql://dev.fake:1234/foobar"))
  (is (= (dissoc (conf/get-all) :bt-database-url)
         {:database-url "sql://dev.fake:1234/foobar"
          :log-level    :debug
          :port         5000})))

(deftest get-from-props-test
  (wrap-fixtures {"database.url" "sql://props:1234"}
                 {"DATABASE_URL" "sql://dev.fake:1234/foobar"}
                 conf/load!)
  (is (= (conf/get :database-url) "sql://props:1234")))

(deftest get-parsed-test
  (wrap-fixtures {"foo" "abcdef"}
                 {"bar" "123"
                  "baz" ":blah"
                  "quux" "[:a \"b\" c]"}
                 conf/load!)
  (is (= "abcdef" (conf/get :foo)))
  (is (= 123 (conf/get :bar)))
  (is (= :blah (conf/get :baz)))
  (is (= [:a "b" 'c] (conf/get :quux))))

(deftest get-not-found-arg-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :xxx) nil))
  (is (= (conf/get :xxx :foobar) :foobar)))

(deftest get-all-test
  (wrap-fixtures {"foo" "bar"} {"baz" "quux"} conf/load!)
  (is (= (dissoc (conf/get-all) :bt-database-url :xyz-database-url)
         {:database-url "sql://fake:1234/foobar"
          :log-level    :debug
          :port         5000
          :foo          "bar"
          :baz          "quux"})))

(deftest set!-test
  (wrap-fixtures {} {} conf/load!)
  (is (= (conf/get :port) 5000))
  (is (= (conf/get :color) nil))
  (conf/set! :color "red")
  (conf/set! :port 5001)
  (is (= (conf/get :port) 5001))
  (is (= (conf/get :color) "red")))

(deftest custom-env-key-test
  (wrap-fixtures {}
                 {"CONF_ENV" "dev"
                  "A_b-C" "prod"}
                 #(conf/load! :a-b-c))
  (is (= (conf/get :database-url) "sql://fake:1234/proddb_b")))

(deftest get-var-test
  (wrap-fixtures {} {"CONF_ENV" "dev"} conf/load!)
  (is (= (conf/get :database-url)     "sql://fake:1234/devdb"))
  (is (= (conf/get :bt-database-url)  "sql://fake:1234/devdb"))
  (is (= (conf/get :xyz-database-url) "sql://fake:1234/devdb")))

(deftest with-conf-test
  (wrap-fixtures {"foo" "abc"} {} conf/load!)
  (conf/with-conf {:bar "123"}
    (is (nil? (conf/get :foo)))
    (is (= "123" (conf/get :bar)))))

(deftest with-overrides-test
  (wrap-fixtures {"foo" "abc"} {} conf/load!)
  (conf/with-overrides {:bar "123"}
    (is (= "abc" (conf/get :foo)))
    (is (= "123" (conf/get :bar)))))
