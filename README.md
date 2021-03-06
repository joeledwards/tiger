# tiger

Interpreter for the Tiger programming language.

- [Princeton project overview](https://www.cs.princeton.edu/~appel/modern/java/project.html)
- [Language Spec](https://cs.nyu.edu/courses/fall13/CSCI-GA.2130-001/tiger-spec.pdf)
- [Reference Manual](https://www.lrde.epita.fr/~tiger/tiger.html#SEC_Contents)

## Implementation

This project implements a streaming parser and interpreter. It is written in Scala with the pipline powered by Akka streams.

Stream stages:
- file source
- decoder
- lexer
- parser
- type-validator
- interpreter

The entry point is `com.buzuli.tiger.Tiger.run` where the stream graph is assembled and run.

## Requirements

- [SBT 1.0+](https://www.scala-sbt.org/)
- [Java 8+](https://www.java.com/en/download/)

## Recommended

- [IntelliJ IDEA](https://www.jetbrains.com/idea/)

## Testing

Run `sbt test` to run through the full test suite.

You can test an individual spec via `sbt testOnly`.

For example, to test only the LexerSpec:
```
$ sbt testOnly com.buzuli.tiger.LexerSpec
```

## Building

The `assembly` plugin is used to generate a fat jar containing all of the dependencies.

```
$ sbt assembly
```

