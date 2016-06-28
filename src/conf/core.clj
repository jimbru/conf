(ns conf.core
  "Library to read configuration values from edn resources, environment
  variables, and Java system properties.

  Basic config values will be loaded from `/resources/conf/base.edn`.

  Different configurations may be specified for different 'environments'.
  The current environment is determined by the special environment variable
  `CONF_ENV` (or the Java property `conf.env`). The value of this variable
  determines the name of an additional edn resource to load. Values set in
  this file will take precedence over those set in the base config. For example,
  to run a program in a production configuration, you might run the command:
  `CONF_ENV=prod lein run`. This would load both the base config file and
  `/resources/conf/prod.edn`, with the latter taking precedence. Note that the
  value of `CONF_ENV` can be an arbitrary string; you can name the
  environment-specific config resources however you like.

  Environment variables are also merged into the config. These take a higher
  precedence and can override any file's value. Names of environment variables
  are normalized from UPPER_UNDERSCORE_CASE strings to more Clojure-esque
  lower-dash-case keywords.

  Finally, Java system properties are also merged into the config. These take
  the highest precedence of all, and can override any other config value.
  Property names are normalized from lower.dot.case to the typical
  lower-dash-case, just like environment variables."
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
        {}  ; Config file not found.
        (edn/read-string (slurp resource))))))

(defn- get-conf-env
  "Parses the special CONF_ENV var from config maps. The value of
  this var informs which additional config files should be loaded."
  [& cfgs]
  (when-let [conf-env (some identity (map :conf-env cfgs))]
    (string/lower-case conf-env)))

(defn load!
  "Loads the config. Usually you won't need to call this directly as
  the config will be automatically loaded when you attempt to access it."
  []
  (let [props     (read-props)
        env       (read-env)
        conf-env (get-conf-env props env)]
    (reset! conf (merge (read-config-file "base")
                        (read-config-file "default")
                        (read-config-file conf-env)
                        env
                        props))))

(defn unload!
  "Unloads the config. This exists mainly to ease testing
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
  (when-not (loaded?)
    (load!))
  (swap! conf assoc k val))
