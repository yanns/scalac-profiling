# Providing Better Compilation Performance Information

[![Build
Status](https://platform-ci.scala-lang.org/api/badges/scalacenter/scalac-profiling/status.svg)](https://platform-ci.scala-lang.org/scalacenter/scalac-profiling)

When compile times become a problem, how can Scala developers reason about
the relation between their code and compile times?

## Goal of the project

The goal of this proposal is to allow Scala developers to optimize their
codebase to reduce compile times, spotting inefficient implicit searches,
expanded macro code, and other reasons that slow down compile times and
decrease developer productivity.

This repository holds the compiler plugin and a fork of mainstream scalac
that will be eventually be merged upstream. This work is prompted by [Morgan
Stanley's proposal](PROPOSAL.md) and was approved in our last advisory board.

## Status

The plan and the changes to the compiler plugin are explained in this README
and the source code.

The required changes to the compiler, [Scalac](http://github.com/scala/scala), are
the following *so far* (more to come):

1. [Collect all statistics and optimize checks](https://github.com/scala/scala/pull/6034).
1. [Initialize statistics per global](https://github.com/scala/scala/pull/6051).

## Information about the setup

The project uses a forked scalac version that is used to compile both the
compiler plugin and several OSS projects from the community. The integration
tests are for now [Circe](https://github.com/circe/circe) and
[Monocle](https://github.com/julien-truffaut/Monocle), and they help us look
into big profiling numbers and detect hot spots and misbehaviours.

If you think a particular codebase is a good candidate to become an integration test, please [open an issue](https://github.com/scalacenter/scalac-profiling/issues/new).

## Plan

The [proposal](PROPOSAL.md) is divided into three main areas:

1. Data generation and capture.
1. Data visualisation and comparison.
1. Reproducibility.

How to tackle each of these problems to make the implementation successful?

### Data generation and capture

The generation of data comes from the guts of the compiler. To optimize for
impact, the collection of information is done in two different places (a
compiler plugin and a forked scalac).

#### Project structure

1. [A forked scalac](scalac/) with patches to collect profiling information.
   All changes are expected to be ported upstream.
1. [A compiler plugin](plugin/) to get information from the macro infrastructure independently
   of the used Scalac version.

The work is split into two parts so that Scala developers that are stuck in previous Scala
versions can use the compiler plugin to get some profiling information about macros.

This structure is more practical because it allow us to evolve things faster in the compiler
plugin, or put there things that cannot be merged upstream.

### Data visualisation and comparison

The profiling data will be accessible in two different ways (provided that
the pertinent profiling flags are enabled):

1. A summary of the stats will be printed out in every compile run.
1. A protobuf file will be generated at the root of the class files directory.
   * The file is generated via protobuf so that it's backwards and forwards binary compatible
   * The protobuf file will contain all the profiling information.

Why a protobuf file instead of a JSON file? Forwards and backwards binary
compatibility is important -- we want our tooling to be able to read files
generated by previous or upcoming versions of the compiler. Our goal is to
create a single tool that all IDEs and third-party tools use to parse and
interpret the statistics from JARs and compile runs.

We're collaborating with [Intellij](https://github.com/JetBrains/intellij-scala) to provide
some of the statistics within the IDE (e.g. macro invocations or implicit searches per line).
We have some ideas to show this information as [heat map](https://en.wikipedia.org/wiki/Heat_map) in the future.

### Reproducibility

Getting reproducible numbers is important to reason about the code and
identifying when a commit increases or decreases compile times with
certainty.

To do so, several conditions must be met: the compiler must be warmed up, the
load in the running computer must be low, and the hardware must be tweaked to
disable options that make executions non reproducible (like Turbo Boost).

However, this warming up cannot be done in an isolated scenario as [Scalac's
benchmarking](https://github.com/scala/compiler-benchmark) infrastructure
does because it doesn't measure the overhead of the build tool calling the
compiler, which can be significant (e.g. in sbt).

As a result, reproducibility must be achieved in the build tool itself. My goal
is to provide an sbt plugin that:

1. Reports whether the cpu load is too high and other things that may affect reproducibility;
1. Warms up the compiler by a configurable amount of iterations; and,
1. Disables parallel builds if enabled to ensure reproducibility.

## Collected data

In the following sections, I elaborate on the collected data that we want to
extract from the compiler as well as technical details for every section in
the [original proposal](PROPOSAL.md).

### Information about macros

Per call-site, file and total:

- [x] How many macros are expanded?
- [x] How long do they take to run?
- [x] How many tree nodes do macros create?
- [ ] What's the ratio of generated code/user-defined code?
- [ ] How many of these tree nodes are discarded? (is this possible?)

### Information about implicit search

Getting hold of this information requires changes in mainstream scalac.

Per call-site, file and total:

- [x] How many implicit searches are triggered per position?
- [x] How many implicit searches are triggered for a given type?
- [ ] How long implicit searches take to run?
- [x] How many implicit search failures are?
- [x] How many implicit search hits are?
- [x] What's the ratio of search failures/hits?

### Ideas to be considered (out of the scope of this project)

#### Tell users how to organize their code to maximize implicit search hits

Based on all the implicit search information that we collect from typer, is
it possible to advice Scala developers how to organize to optimize implicit
search hits?

For instance, if we detect that typer is continuosly testing implicit
candidates that fail but have higher precedence (because of implicit search
priorities or implicit names in scope), can we develop an algorithm that
understands priorities and is able to tell users "remove that wildcard
import" or "move that implicit definition to a higher priority scope, like
X"?

(My hunch feeling is that we can, but this requires testing and a deeper
investigation.)

#### Report on concrete, inefficient macros

Macro-generated code is usually inefficient because macro authors do not
optimize for compactness and compile times and express the macro logic with
high-level Scala.

Instead, they could use low-level constructs that spare work to the compiler
(manually generating getters and setters, code-generating shorter fresh
names, spare use of `final` and `private[this]` flags, explicitly typing all
the members, avoiding the use of traits, et cetera).

A well-known problem of macros is that different call-sites that invoke a
macro with the same inputs will generate different trees with identical
semantics. This lack of caching at the macro level is one of the main
problems affecting compile times.

Ideally, this plugin would be able to:

1. Identify inefficient expanded code with tree-size heuristics and the use
   of particular features that could be expressed in a more low-level manner.
1. Tell users if there's any repetition in the expanded code.
1. Let users inspect the macro generated code to manually investigate inefficient
   macro expansions. The generated code could be written in a directory passed in
   via compiler plugin settings, and would be disabled by default.

As a side note, repetitions in expanded code can be addressed in two ways:

* Create a cache of expanded code in the compiler macro infrastructure.
* Create a cache of expanded code in the macro implementation.

Both alternatives are **challenging**, if not impossible. The easiest way to
cache implicits is that the developers of implicit-intensive codebases create
their own objects storing implicit values for all the target types and
imports them in all the use sites.

#### More to come...

### Results

#### What the proposal wants

- [x] Compilation time per file (*this is provided by `-Ystatistics`*)
  - [x] Total
  - [x] Broken down by phase
- [x] Times per macro (*this is provided by the macro plugin*)
  - [x] Per file
  - [x] Per macro
    - [x] Invocations
    - [x] Total time
- [x] Implicit search details (time and number)
  - [x] By type
  - [x] By invocation (only number for now)
  - [ ] By file
- [ ] Time for flagged features (for certain features – e.g. optimisation)
  * Unknown at this point: how can we efficiently capture this information?
- [ ] Time resolving types from classpath
  - [x] Total
  - [ ] by jar (**not possible**)
- [ ] Imports – unused/wildcard timings? (**unnecessary**)