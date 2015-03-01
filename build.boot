(set-env! :dependencies '[[boot/core "2.0.0-rc10"]
                          [boot/worker "2.0.0-rc10"]
                          [ewen.boot/boot-misc "0.0.1"]]
          :source-paths #{"src/clj"})

(require '[ewen.boot.boot-maven :refer [gen-pom]]
         '[ewen.boot.boot-misc :refer [add-src]])

(let [pom-opts {:project 'ewen.boot/boot-maven
                :version "0.0.1"}]
     (task-options!
       pom pom-opts
       gen-pom pom-opts))

(deftask build []
         (comp (pom) (add-src) (jar) (install)))