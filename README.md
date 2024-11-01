Joern Benchmarks
================

A repository for running Joern against known benchmarks.

## Usage

```bash
$ sbt stage
$ ./joern-benchmarks --help
joern-benchmark v0.0.1
Usage: joern-benchmark [options] benchmark frontend

A benchmarking suite for Joern
  -h, --help
  --version                Prints the version
  benchmark                The benchmark to run. Available [SECURIBENCH_MICRO,ICHNAEA,THORAT]
  frontend                 The frontend to use. Available [JAVASRC,JAVA,JSSRC,PYSRC,SEMGREP,CODEQL]
  -d, --dataset-dir <value>
                           The dataset directory where benchmarks will be initialized and executed. Default is `./workspace`.
  -o, --output <value>     The output directory to write results to. Default is `./results`.
  -f, --format <value>     The output format to write results as. Default is MD. Available [JSON,CSV,MD]
  -k, --max-call-depth <value>
                           The max call depth `k` for the data-flow engine. Has no effect on non-Joern frontends. Default is 5.
  -i, --iterations <value>
                           The number of iterations for a given benchmark. Default is 1.
```

Example of testing for various values of `k`:
```
for k in {0..5}; do ./joern-benchmarks DEFECTS4J JAVA -f CSV -J-Xmx8G -i 1 -k $k; done 
```

## Data-Flow Benchmarks

The benchmark naming convention of `<BENCHMARK>_<FRONTEND>`, e.g. `OWASP_JAVA` runs `OWASP` using the `jimple2cpg`
frontend (JVM bytecode).

| Benchmark                                                                                      | Enabled Frontends                   |
|------------------------------------------------------------------------------------------------|-------------------------------------|
| [`SECURIBENCH_MICRO`](https://github.com/too4words/securibench-micro)                          | `JAVASRC` `JAVA` `SEMGREP` `CODEQL` |
| [`ICHNAEA`](https://www.franktip.org/pubs/tse2020.pdf)                                         | `JSSRC` `SEMGREP`                   |
| [`THORAT`](https://github.com/DavidBakerEffendi/benchmark-for-taint-analysis-tools-for-python) | `PYSRC` `SEMGREP` `CODEQL`          |

### Joern

Joern's open-source data-flow engine is enabled whenever a Joern frontend is selected, e.g. `JAVA`, `PYSRC`, etc.

### Semgrep

If `SEMGREP` is selected, this requires an installation of Semgrep where `semgrep scan` will be used to
initiate the process. Custom rules specific to benchmarks can be found under `src/main/resources/semgrep`.

Note: Only results with data-flow traces are considered as findings.

### CodeQL

If `CODEQL` is selected, this requires an installation of CodeQL CLI where `codeql` will be used to
create the database and run the scans. Custom rules specific to benchmarks can be found under `src/main/resources/codeql`.

## Notes

Benchmarks successfully tested on the following versions of target software:

* Joern v4.0.119
* Semgrep v1.93.0 
* CodeQL v2.19.2
