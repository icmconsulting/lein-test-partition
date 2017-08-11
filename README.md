# lein-test-partition

Easily partition your `clojure.test` test suite across multiple JVMs.

## Use case
You have a large test suite that take a long time to run - for example, your suite involves UI tests (using selenium, perhaps).
Most CI tools support [parallelisation](https://jenkins.io/doc/pipeline/examples/#parallel-multiple-nodes) within a build pipeline.

`lein-test-partition` allows you to partition your test suite to run across multiple parallel nodes.

## Usage

Put `[lein-test-partition "1.0.0"]` into the `:plugins` vector of your project.clj.

When running tests using `lein-test-partition`, specify the total number of partitions, and the partition number to execute.
Note: you would use this instead of your regular test runner command (e.g. `lein test`) 

`lein-test-partition` supports two different strategies for partitioning your suite.

### By individual test (`deftest`) `part-tests`
Tests are logically grouped together by the hash of the resolved test identifier (i.e. namespace/test-symbol).
Best strategy to use to achieve a more even spread of tests across nodes.

### By namespace `part-ns`
All tests within the same namespace are grouped and executed together on the same node.
Best strategy to use if a namespace has expensive `:all` fixtures. 

## Examples

Split your tests by namespace across 2 build nodes.

```
# On node 1:
$ lein test-partition part-ns :part 1 :of 2

# on node 2:
$ lein test-partition part-ns :part 2 :of 2

```

Split all your tests, across 4 build nodes
```
# On node 1:
$ lein test-partition part-tests :part 1 :of 4

# on node 2:
$ lein test-partition part-tests :part 2 :of 4

# on node 3:
$ lein test-partition part-tests :part 3 :of 4

# on node 4:
$ lein test-partition part-tests :part 4 :of 4
```

## Notes

### Test selectors
`lein-test-partition` honours leiningen's [test selectors](https://github.com/technomancy/leiningen/blob/f63d494f8446e5e21f8a67634d03bf2973612da4/sample.project.clj#L346).

```
# On node 1:
$ lein test-partition part-tests :part 1 :of 2 :integration

# on node 2:
$ lein test-partition part-tests :part 2 :of 2 :integration
```

### Using other `clojure.test` runners/plugins
You can use `lein-test-partition` with any leiningen test runner plugin as long as it supports whitelisting tests using the `:only` option. 
This is possible by simply adding a command `:alias` to your leiningen project.clj.
 
For example, to use [`test2junit`](https://github.com/ruedigergad/test2junit):

In project.clj, (perhaps in your CI profile):
```
...
:aliases  {"test" ["test2junit"]}
...
```

## License

Copyright © 2017 ICM Consulting

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
