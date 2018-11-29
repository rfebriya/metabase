(ns metabase.plugins.initialize
  (:require [metabase.plugins.util :as plugins.u]
            metabase.util
            )
  (:import [java.sql Driver DriverManager]))

(defmulti ^:private do-init-step!
  {:arglists '([m])}
  (comp keyword :step))

(defmethod do-init-step! :load-namespace [{nmspace :namespace}]
  (println "[INIT PLUGIN] LOADING NAMESPACE ::" nmspace)
  (require (symbol nmspace)))

(defn- proxy-driver ^Driver [^Driver driver]
  (reify Driver
    (acceptsURL [_ url]
      (.acceptsURL driver url))
    (connect [_ url info]
     (println "[[connect]]") ; NOCOMMIT
      (.connect driver url info))
    (getMajorVersion [_]
      (.getMajorVersion driver))
    (getMinorVersion [_]
      (.getMinorVersion driver))
    (getParentLogger [_]
      (.getParentLogger driver))
    (getPropertyInfo [_ url info]
      (.getPropertyInfo driver url info))
    (jdbcCompliant [_]
      (.jdbcCompliant driver))))

(defmethod do-init-step! :register-jdbc-driver [{class-name :class}]
  (println "[INIT PLUGIN] REGISTERING JDBC DRIVER ::" class-name)
  (let [klass  (plugins.u/class-for-name class-name)
        driver (proxy-driver (.newInstance klass))]
    (DriverManager/registerDriver driver)

    ;; deregister the non-proxy version of the driver if it got in there somehow
    (doseq [driver (enumeration-seq (DriverManager/getDrivers))
            :when (instance? klass driver)]
      (println "DEREGISTER ::" driver) ; NOCOMMIT
      (DriverManager/deregisterDriver driver))

    ;; NOCOMMIT
    (println "DRIVERS:" (metabase.util/pprint-to-str 'blue (enumeration-seq (DriverManager/getDrivers))))
    ))


(defn initialize! [init-steps]
  (doseq [step init-steps]
    (do-init-step! step)))
