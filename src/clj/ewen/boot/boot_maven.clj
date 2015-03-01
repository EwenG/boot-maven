(ns ewen.boot.boot-maven
  (:require [boot.xml :as xml]
            [clojure.java.io :as io]
            [boot.git :as git]
            [boot.core :as core :refer [deftask get-env]]
            [boot.util :as util]
            [boot.pod :as pod])
  (:import (java.util Properties)))

(xml/decelems
  artifactId connection description dependencies dependency exclusion
  exclusions developerConnection enabled groupId id license licenses
  modelVersion name project scope tag url scm version comments
  build plugins plugin executions execution phase configuration
  sources source resources resource directory targetPath goals goal)

(defn pom-xml [{p :project v :version d :description l :license
                {su :url st :tag} :scm u :url deps :dependencies
                source-paths :source-paths resource-paths :resource-paths
                target-path :target-path :as env}]
  (let [[g a] (util/extract-ids p)
        ls (map (fn [[name url]] {:name name :url url}) l)]
    (project
      :xmlns "http://maven.apache.org/POM/4.0.0"
      :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance"
      :xsi:schemaLocation "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
      (modelVersion "4.0.0")
      (groupId g)
      (artifactId a)
      (version v)
      (name a)
      (description d)
      (url u)
      (licenses
        (for [{ln :name lu :url lc :comments} ls]
          (license
            (url lu)
            (name ln)
            (comments lc))))
      (scm
        (url su)
        (tag (or st "HEAD")))
      (dependencies
        (for [[p v & {es :exclusions s :scope}] deps
              :let [[g a] (util/extract-ids p)]]
          (dependency
            (groupId g)
            (artifactId a)
            (version v)
            (scope (or s "compile"))
            (exclusions
              (for [p es :let [[g a] (util/extract-ids p)]]
                (exclusion
                  (groupId g)
                  (artifactId a)))))))
      (build
        (directory target-path)
        (resources
          (for [resource-path resource-paths]
            (resource
              (directory resource-path))))
        (plugins
          (plugin
            (groupId "org.codehaus.mojo")
            (artifactId "build-helper-maven-plugin")
            (version "1.9.1")
            (executions
              (execution
                (id "add-source")
                (phase "generate-sources")
                (goals
                  (goal "add-source"))
                (configuration
                  (sources
                    (for [source-path source-paths]
                      (source source-path))))))))))))

(defn spit-pom! [xmlpath proppath {:keys [project version] :as env}]
  (let [[gid aid] (util/extract-ids project)
        prop (doto (Properties.)
               (.setProperty "groupId" gid)
               (.setProperty "artifactId" aid)
               (.setProperty "version" version))
        xmlfile (doto (io/file xmlpath) io/make-parents)
        propfile (doto (io/file proppath) io/make-parents)]
    (spit xmlfile (pr-str (pom-xml env)))
    (with-open [s (io/output-stream propfile)]
      (.store prop s (str gid "/" aid " " version " property file")))))


(deftask gen-pom
         "Create project pom.xml file.
         The project and version must be specified to make a pom.xml."
         [p project SYM sym "The project id (eg. foo/bar)."
          v version VER str "The project version."
          d description DESC str "The project description."
          u url URL str "The project homepage url."
          l license NAME:URL {str str} "The project license map."
          s scm KEY=VAL {kw str} "The project scm map (KEY in url, tag)."]
         (let [project-dir (core/temp-dir!)]
           (core/with-pre-wrap fileset
                               (let [tag (or (:tag scm) (util/guard (git/last-commit)) "HEAD")
                                     scm (when scm (assoc scm :tag tag))
                                     opts (assoc *opts* :scm scm :dependencies (:dependencies (core/get-env)))]
                                 (core/empty-dir! project-dir)
                                 (when-not (and project version)
                                   (throw (Exception. "need project and version to create pom.xml")))
                                 (let [xmlfile (io/file "pom.xml")
                                       propfile (io/file "pom.properties")]
                                   (util/info "Writing %s and %s...\n" (.getName xmlfile) (.getName propfile))
                                   (spit-pom! (.getPath xmlfile) (.getPath propfile) (merge (get-env) opts))
                                   (-> fileset (core/add-resource project-dir) core/commit!))))))
