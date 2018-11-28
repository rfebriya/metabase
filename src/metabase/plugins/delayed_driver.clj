(ns metabase.plugins.delayed-driver
  (:require [metabase.driver :as driver]
            [metabase.plugins.initialize :as plugins.init])
  (:import clojure.lang.MultiFn))

(defn- driver-multimethods []
  (for [[_ varr] (ns-interns 'metabase.driver)
        :when (instance? MultiFn @varr)]
    @varr))

(defn- remove-methods [driver]
  (doseq [multifn (driver-multimethods)]
    (remove-method multifn driver)))

(defn register-delayed-load-driver!
  [{init-steps :init, {driver-name :name, :keys [display-name connection-properties]} :driver}]
  (let [driver (keyword driver-name)

        initialize!
        (fn []
          (println "INITIALIZE!") ; NOCOMMIT
          (remove-methods driver)
          (plugins.init/initialize! init-steps))

        add-impl
        (fn [^MultiFn multifn, f]
          (.addMethod multifn driver f))

        add-delayed-load-impl
        (fn [^MultiFn multifn]
          (add-impl multifn (fn [& args]
                              (initialize!)
                              (apply multifn args))))]

    (doseq [multifn (driver-multimethods)]
      (add-delayed-load-impl multifn))

    (add-impl driver/available? (constantly true))
    (add-impl driver/display-name (constantly display-name))
    (add-impl driver/connection-properties (constantly connection-properties))

    (driver/register! driver)))
