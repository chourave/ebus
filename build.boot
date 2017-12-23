(def +project+ 'chourave/ebus)
(def +version+ "0.1-SNAPSHOT")

(set-env!
  :resource-paths #{"src"}
  :exclusions '[org.clojure/clojure org.clojure/tools.reader]
  :dependencies
  [
   ; Development
   '[clj-async-test "0.0.5" :scope "test"]
   '[metosin/boot-alt-test "0.3.2" :scope "test"]
   '[onetom/boot-lein-generate "0.1.3" :scope "test"]

   '[com.taoensso/timbre "4.10.0"]
   '[org.clojure/clojure "1.9.0-RC2"]
   '[org.clojure/core.async "0.3.465"]
   '[org.clojure/tools.reader "1.1.1"]])

(task-options!
  pom {:project     +project+
       :version     +version+
       :description "Minimal event bus"
       :url         "https://github.com/chourave/ebus"
       :scm         {:url "https://github.com/chourave/ebus"}
       :license     {"EPL" "http://www.eclipse.org/legal/epl-v10.html"}})

(require
  '[boot.lein :as lein]
  '[boot.pod :as pod]
  '[metosin.boot-alt-test :refer (alt-test)])

(lein/generate)

(deftask testing
  "Profile setup for running tests."
  []
  (merge-env! :source-paths #{"test"})
  identity)

(deftask test-clj
         "Run unit tests"
         []
         (comp
           (testing)
           (watch)
           (alt-test :report 'eftest.report.pretty/report)))

(deftask check-conflicts
  "Verify there are no dependency conflicts."
  []
  (with-pass-thru fs
    (require '[boot.pedantic :as pedant])
    (let [dep-conflicts (resolve 'pedant/dep-conflicts)]
      (if-let [conflicts (not-empty (dep-conflicts pod/env))]
        (throw (ex-info (str "Unresolved dependency conflicts. "
                             "Use :exclusions to resolve them!")
                        conflicts))
        (println "\nVerified there are no dependency conflicts.")))))

(deftask check-deps
  ""
  []
  (comp
    (show :updates true)
    (check-conflicts)))
