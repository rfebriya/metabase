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
  [{init-steps :init, {driver-name :name, :keys [display-name connection-properties parent]} :driver}]
  (let [driver (keyword driver-name)]
    (doseq [[^MultiFn multifn, f] (partition
                                   2
                                   [driver/initialize!
                                    (fn [_]
                                      (println "\n\n[ LOAD DELAYED LOAD DRIVER" driver "]\n\n") ; NOCOMMIT
                                      (plugins.init/initialize! init-steps))

                                    driver/available?
                                    (constantly true)

                                    driver/display-name
                                    (constantly display-name)

                                    driver/connection-properties
                                    (constantly connection-properties)])]
      (.addMethod multifn driver f))

    (println "[ REGISTERING DELAYED LOAD DRIVER" driver "( parent" parent " ) ]") ; NOCOMMIT
    (driver/register! driver, :parent (keyword parent))))
