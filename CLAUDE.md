# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Java Maven project (`rpc-demo`) - a starting point for building an RPC (Remote Procedure Call) framework demonstration.

- **Language**: Java 17
- **Build System**: Maven
- **Group ID**: com.baichen

## Commands

```bash
# Compile the project
mvn compile

# Build JAR package
mvn package

# Clean build artifacts
mvn clean

# Install to local repository
mvn install
```

Note: Currently no test framework is configured and no tests exist in `src/test/`.

## Architecture

This is a skeleton project intended for building an RPC framework. Future development will likely add:
- Networking components (Netty, socket connections)
- Serialization (JSON, Protobuf, etc.)
- Service registry and discovery
- RPC client and server implementations
