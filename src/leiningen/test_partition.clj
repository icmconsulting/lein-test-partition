(ns leiningen.test-partition
  (:require [leiningen.core.main :as lmain]
            [leiningen.core.eval :as eval]
            [leiningen.core.project :as project]
            [bultitude.core :as b]
            [clojure.java.io :as io]))

(defn test?-form
  [project-selectors selectors]
  `(fn [v#]
     (let [m# (meta (val v#))]
       ((apply every-pred :test ~(vec (vals (select-keys project-selectors selectors)))) m#))))

(defn all-namespaces
  [project]
  (b/namespaces-on-classpath
    :classpath (map io/file (distinct (:test-paths project)))
    :ignore-unreadable? false))

(defn assert-args
  [task {:keys [part of]}]
  (assert (and part of) "Must include both a :part and :of argument.")
  (assert (and (integer? part) (integer? of)) ":part and :of arguments must be numbers.")
  (assert (and (pos? part) (pos? of)) ":part and :of arguments must be greater than 0.")
  (assert (<= part of) ":part number must be less than or equal to :of")
  (assert (#{"part-ns" "part-tests"} task) "Missing task - expected either 'part-ns' or 'part-tests'"))

(defn hash-for-task
  [task test]
  (case task
    "part-ns" (-> test symbol namespace hash)
    "part-tests" (-> test hash)))

(defn parse-args
  [args]
  (let [[parsed-args] (lmain/parse-options args)]
    (-> parsed-args
        (update :part read-string)
        (update :of read-string))))

(defn test-partition
  "Partitions all the tests in the project, so that they can be executed on separate, probably parallel, build nodes, and
  then executes all tests in that partition.

  Useful for CI environments that allow executing test phases across multiple nodes.

  The following tasks are available:
  - part-ns: partition the tests by namespace. All tests in the same namespace will be run on the same node. Useful when there is high overhead in :once fixtures.
  e.g. lein test-partition part-ns :node 1 :of 2
  - part-tests: partition by the individual tests. Tests aren't logicially grouped. Good for proving that there aren't inter-test dependencies!
  e.g. lein test-partition part-tests :part 1 :of 2

  Each task requires:
  - :part - the partition number (e.g. 1) of tests to execute
  - :of - the total number of partitions (nodes, if you want)

  Optional:
  - Specify test selectors
  e.g. lein test-partition part-tests :part 1 :of 3 :integration

  All arguments after the :part and :of arguments are considered test selectors."
  [project task & args]
  (let [{:keys [part of] :as parsed-args} (parse-args args)
        _ (assert-args task parsed-args)
        project (project/merge-profiles project [:leiningen/test :test])
        all-namespaces (all-namespaces project)
        selectors (keys (dissoc parsed-args :of :part))
        project-selectors (:test-selectors project)
        tests-file (str "target/all-tests" part)
        _ (eval/eval-in-project project
                                `(->> (reduce (fn [tests# namespace#]
                                                (concat tests#
                                                        (->>
                                                          (ns-publics (last namespace#))
                                                          (filter ~(test?-form project-selectors selectors))
                                                          (map (comp (partial symbol (name (last namespace#))) name key)))))
                                              [] '~(map (fn [ns] `'~ns) all-namespaces))
                                      (map str)
                                      (clojure.string/join " ")
                                      (spit ~tests-file))
                                (apply list 'do (map (fn [ns] (list 'require `'~ns)) all-namespaces)))
        all-tests (clojure.string/split (slurp tests-file) #" ")
        tests-for-node (filter (every-pred #(= (mod (hash-for-task task %) of) (dec part))
                                           (complement clojure.string/blank?))
                               all-tests)]
    (lmain/info "Test node [" part "] of [" of "]")
    (if (seq tests-for-node)
      (do
        (lmain/info "Running tests: " tests-for-node)
        (lmain/resolve-and-apply project (into ["test" ":only"] tests-for-node)))
      (lmain/info "No tests to run for this node"))))
