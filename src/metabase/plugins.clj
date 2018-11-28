(ns metabase.plugins
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [dynapath.util :as dynapath]
            [metabase
             [config :as config]
             [util :as u]]
            [metabase.plugins
             [initialize :as plugins.init]
             [util :as plugins.u]]
            [metabase.plugins.delayed-driver :as plugins.delayed-driver]
            [metabase.util
             [date :as du]
             [i18n :refer [trs]]]
            [yaml.core :as yaml])
  (:import java.io.File
           java.net.URL
           [java.nio.file CopyOption Files FileSystems LinkOption Path]
           java.nio.file.attribute.FileAttribute
           java.util.Collections
           java.util.zip.ZipFile))

(defn- plugins-dir-filename ^String []
  (or (config/config-str :mb-plugins-dir)
      (str (System/getProperty "user.dir") "/plugins")))

(defn- ^:deprecated plugins-dir
  "The Metabase plugins directory. This defaults to `plugins/` in the same directory as `metabase.jar`, but can be
  configured via the env var `MB_PLUGINS_DIR`."
  ^File []
  (io/file (plugins-dir-filename)))

(defn- plugins-dir-path
  (^Path []
   (.getPath (FileSystems/getDefault) (plugins-dir-filename) (make-array String 0)))
  (^Path [^String filename]
   (.getPath (FileSystems/getDefault) (plugins-dir-filename) (into-array String [filename]))))

(defn- make-plugins-dir-if-needed! []
  (let [plugins-path (plugins-dir-path)]
    (when-not (Files/exists plugins-path (make-array LinkOption 0))
      (Files/createDirectory plugins-path (make-array FileAttribute 0)))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                              Add JAR to Classpath                                              |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- add-jar-to-classpath!
  "Dynamically add a JAR file to the classpath."
  ^URL [^URL jar-url]
  (when-let [sysloader (plugins.u/system-classloader)]
    (dynapath/add-classpath-url sysloader jar-url)
    (log/info (trs "Added {0} to classpath" jar-url))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                          Add JAR-in-JAR to Classpath                                           |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- extract-system-modules-in-path! [^Path modules-path]
  (doseq [^Path path (iterator-seq (.iterator (Files/list modules-path)))
          :let       [target  (plugins-dir-path (str (.getFileName path)))
                      exists? (Files/exists target (make-array LinkOption 0))]
          :when      (not exists?)]
    (du/profile (str "EXTRACT " path " -> " target)
      (Files/copy path target ^"[Ljava.nio.file.CopyOption;" (make-array CopyOption 0)))))

(defn- extract-modules-from-jar! [^URL modules-url]
  (with-open [fs (FileSystems/newFileSystem (.toURI modules-url) Collections/EMPTY_MAP)]
    (let [modules-path (.getPath fs "/" (into-array String ["modules"]))]
      (extract-system-modules-in-path! modules-path))))

(defn- extract-modules-from-resources-dir! [^URL modules-url]
  (let [modules-path (.getPath (FileSystems/getDefault) "/" (into-array String [(.getPath modules-url)]))]
    (extract-system-modules-in-path! modules-path)))

(defn- extract-system-modules!
  ([]
   (extract-system-modules! (io/resource "modules")))
  ([^URL modules-url]
   (let [inside-jar? (str/includes? (.getFile modules-url) ".jar!/")]
     ((if inside-jar?
         extract-modules-from-jar!
         extract-modules-from-resources-dir!) modules-url))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                               Initialize Plugin                                                |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- plugin-info [^URL jar-url]
  (let [zip-file (ZipFile. (io/file jar-url))]
    (when-let [entry (.getEntry zip-file "metabase-plugin.yaml")]
      (let [contents (with-open [is (.getInputStream zip-file entry)]
                       (slurp is))]
        (yaml/parse-string contents)))))

(defn- init-plugin! [^URL jar-url]
  (when-let [{init-steps :init, {:keys [delay-loading]} :driver, :as info} (plugin-info jar-url)]
    (if delay-loading
      (plugins.delayed-driver/register-delayed-load-driver! info)
      (plugins.init/initialize! init-steps))))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                 Load MB Plugin                                                 |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- load-plugin! [^URL jar-url]
  (add-jar-to-classpath! jar-url)
  (init-plugin! jar-url))


;;; +----------------------------------------------------------------------------------------------------------------+
;;; |                                                 load-plugins!                                                  |
;;; +----------------------------------------------------------------------------------------------------------------+

(defn- dynamically-add-jars!
  "Dynamically add any JARs in the `plugins-dir` to the classpath.
   This is used for things like custom plugins or the Oracle JDBC driver, which cannot be shipped alongside Metabase
  for licensing reasons."
  []
  (when-let [dir (plugins-dir)]
    (log/info (trs "Loading plugins in directory {0}..." dir))
    (doseq [^File file (.listFiles dir)
            :when (and (.isFile file)
                       (.canRead file)
                       (re-find #"\.jar$" (.getPath file)))]
      (log/info (u/format-color 'magenta (trs "Loading plugin {0}... {1}" file (u/emoji "🔌"))))
      (load-plugin! (io/as-url file)))))

(defn load-plugins! []
  (make-plugins-dir-if-needed!)
  (extract-system-modules!)
  (dynamically-add-jars!))

(defn ^:deprecated setup-plugins! [])
