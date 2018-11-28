(ns metabase.plugins.initialize
  (:require [metabase.plugins.util :as plugins.u])
  (:import [java.sql Driver DriverManager]
           java.util.Properties))

(defmulti ^:private do-init-step!
  {:arglists '([m])}
  (comp keyword :step))

(defmethod do-init-step! :load-namespace [{nmspace :namespace}]
  (println "[INIT PLUGIN] LOADING NAMESPACE ::" nmspace)
  (require (symbol nmspace)))

(defn- proxy-driver ^Driver [^Driver driver]
  (proxy [Driver] []
    (acceptsURL [^String url]
      (.acceptsURL driver url))
    (connect [^String url, ^Properties info]
      (.connect driver url info))
    (getMajorVersion []
      (.getMajorVersion driver))
    (getMinorVersion []
      (.getMinorVersion driver))
    (getParentLogger []
      (.getParentLogger driver))
    (getPropertyInfo [^String url, ^Properties info]
      (.getPropertyInfo driver url info))
    (jdbcCompliant []
      (.jdbcCompliant driver))))

(defmethod do-init-step! :register-jdbc-driver [{class-name :class}]
  (println "[INIT PLUGIN] REGISTERING JDBC DRIVER ::" class-name)
  (let [klass  (plugins.u/class-for-name class-name)
        driver (proxy-driver (.newInstance klass))]
    (DriverManager/registerDriver driver)))


(defn initialize! [init-steps]
  (doseq [step init-steps]
    (do-init-step! step)))
