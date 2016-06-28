# conf

[![Build Status](https://travis-ci.org/jimbru/conf.svg?branch=master)](https://travis-ci.org/jimbru/conf)
[![Clojars Project](https://img.shields.io/clojars/v/conf.svg)](https://clojars.org/conf)

A simple environment configuration library for Clojure.

## Installation

Add the following dependency to your `project.clj` file:

[![Clojars Project](http://clojars.org/conf/latest-version.svg)](http://clojars.org/conf)

## Usage

1. Add a base config file.

    Create a new file at `/resources/conf/base.edn`. This will be the base
    configuration that's loaded every time your app runs. For example:
    ```edn
    {:foo "bar"
     :port 5000}
    ```

2. (Optional) Add environment-specific config files.

    If desired, you can set additional environment-specific config files for
    your app. Add additional files at `/resources/conf/${ENV}.edn` where $ENV is
    any string of your choosing. For example, following on the example above, if
    in production you'd like your app to run on port 80, you might create
    `/resources/conf/prod.edn` to look like this:
    ```edn
    {:port 80}
    ```

3. Add code in your app to use your config values.

    For example:
    ```clojure
    (require '[conf.core :as conf])

    (def foo (conf/get :foo))

    (defn -main [& args]
      (println "foo is" foo)
      (start-server {:port (conf/get :port)}))
    ```

4. Run your app.

    To use just the base config, run your app normally, e.g. `lein run`. If
    you'd like to enable an environment-specific config, be sure to pass the
    `CONF_ENV` environment variable, e.g. `CONF_ENV=prod lein run`.

## License

Copyright © 2014-present Jim Brusstar, Standard Treasury

Distributed under the terms of the MIT License.
