(ns metabase.plugins.util
  (:require [clojure.tools.logging :as log]
            [metabase.util.i18n :refer [trs]])
  (:import clojure.lang.DynamicClassLoader))

(defn system-classloader
  ^DynamicClassLoader []
  (let [sysloader (ClassLoader/getSystemClassLoader)]
    (if-not (instance? DynamicClassLoader sysloader)
      (log/error (trs "Error: System classloader is not an instance of clojure.lang.DynamicClassLoader.")
                 (trs "Make sure you start Metabase with {0}"
                      "-Djava.system.class.loader=clojure.lang.DynamicClassLoader"))
      sysloader)))

(defn class-for-name ^Class [^String classname]
  (Class/forName classname (boolean :initialize) (system-classloader)))
