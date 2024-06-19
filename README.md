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
  --disable-semantics      Disables the user-defined semantics for Joern data-flows. Has no effect on non-Joern frontends.
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

## ICSME 2024

To reproduce the results for the ICSME tool paper, make sure of the following:

* This repo is on the `ICSME-2024` tag
* Semgrep version 1.74.0 is installed
* CodeQL version 2.17.04 is installed

The following headers provide commands to reproduce the results in Table 1. See the `./results` directory for the output.

#### Joern

* `joern-benchmarks SECURIBENCH_MICRO JAVASRC --disable-semantics`
* `joern-benchmarks THORAT PYSRC --disable-semantics`
* `joern-benchmarks ICHNAEA JSSRC --disable-semantics`

#### Joern*

* `joern-benchmarks SECURIBENCH_MICRO JAVASRC`
* `joern-benchmarks THORAT PYSRC`
* `joern-benchmarks ICHNAEA JSSRC`

#### Semgrep

* `joern-benchmarks SECURIBENCH_MICRO SEMGREP`
* `joern-benchmarks THORAT SEMGREP`
* `joern-benchmarks ICHNAEA SEMGREP`

#### CodeQL

* `joern-benchmarks SECURIBENCH_MICRO SEMGREP`
* `joern-benchmarks THORAT SEMGREP`
* `joern-benchmarks ICHNAEA SEMGREP`

Note: Joern and Joern* results overwrite one another.