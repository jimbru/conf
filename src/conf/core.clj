(ns clams.conf
  "Provides app configuration. Clams app configuration is read from EDN files
  compiled into resources, environment variables, and Java system properties.
  This namespace contains both the infrastructure for loading these values
  and accessing them at run-time.

  Clams provides a sane configuration out-of-the-box in its base config file.
  This should be sufficient for the simplest apps. An app may override these
  settings and add custom values by creating an additional config file at
  `/resources/conf/default.edn`.

  Apps may also add environment-specific config files. These are selectable at
  run-time by setting the special environment variable `CLAMS_ENV` (or the Java
  property `clams.env`). These config values are merged on top of, and take
  precedence over, the default and base configs. For example, to activate a
  production-specific configuration, one might run the command:
  `CLAMS_ENV=prod lein run`. The value of `CLAMS_ENV` can be an arbitrary
  string; the value of that string determines which config is loaded.

  Environment variables are also merged into the config. These take a higher
  precedence and can override any file's value. Names of environment variables
  are normalized from UPPER_UNDERSCORE_CASE strings to more Clojure-esque
  lower-dash-case keywords.

  Finally, Java system properties are also merged into the config. These take
  the highest precedence of all, and can override any other config value.
  Property names are normalized from lower.dot.case to the typical
  lower-dash-case, just like environment variables.
  "
  (:refer-clojure :rename {get core-get set! core-set!})
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as string]))

(def ^:private conf (atom nil))

(defn- normalize-var-name
  "Environment variables are often written in UPPER_UNDERSCORE_CASE. Likewise,
  Java system properties are usually written in lower.dot.case. Since Clojure
  keywords are ordinarily written in lower-dash-case, this function normalizes
  them all to match."
  [name]
  (if-not (nil? name)
    (-> name
        string/lower-case
        (string/replace "_" "-")
        (string/replace "." "-")
        keyword)))

(defn- normalize-var-map
  "Normalizes a config var map."
  [vmap]
  (into {} (for [[k v] vmap]
    [(normalize-var-name k) v])))

(defn- sys-getenv
  "Wrapper around System/getenv. Makes it easier to mock in tests."
  []
  (System/getenv))

(defn- sys-getproperties
  "Wrapper around System/getProperties. Makes it easier to mock in tests."
  []
  (System/getProperties))

(defn- read-env
  "Reads environment variable configs."
  []
  (normalize-var-map (sys-getenv)))

(defn- read-props
  "Reads Java system properties configs."
  []
  (normalize-var-map (sys-getproperties)))

(defn- read-config-file
  "Reads from resources the config file of the given name and processes it
  into a normalized map."
  [name]
  (when name
    (let [resource (io/resource (format "conf/%s.edn" name))]
      (if (nil? resource)
        {}  ;; Config file not found.
        (edn/read-string (slurp resource))))))

(defn- get-clams-env
  "Parses the special CLAMS_ENV var from config maps. The value of
  this var informs which additional config files should be loaded."
  [& cfgs]
  (when-let [clams-env (some identity (map :clams-env cfgs))]
    (string/lower-case clams-env)))

(defn load!
  "Loads the app config. Usually you won't need to call this directly as
  the config will be automatically loaded when you attempt to access it."
  []
  (let [props     (read-props)
        env       (read-env)
        clams-env (get-clams-env props env)]
    (reset! conf (merge (read-config-file "base")
                        (read-config-file "default")
                        (read-config-file clams-env)
                        env
                        props))))

(defn unload!
  "Unloads the app config. This exists mainly to ease testing
  and should very rarely, if ever, be called in your app."
  []
  (reset! conf nil))

(defn loaded?
  "Tests whether the config has been loaded."
  []
  (not (nil? @conf)))

(defn get-all
  "Returns the entire config map."
  []
  (when-not (loaded?)
    (load!))
  @conf)

(defn get
  "Returns the config value for the given key. If a not-found argument is
  passed, that will be returned if no value is found, otherwise nil."
  ([k]
    (get k nil))
  ([k not-found]
   (core-get (get-all) k not-found)))

(defn set!
  "Sets the config value for the given key. This is useful when
   debugging in the repl."
  [k val]
  (get-all)                             ; For the side effect
  (swap! conf assoc k val))
