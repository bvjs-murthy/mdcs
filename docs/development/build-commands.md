# Compile / Build / Execute Commands (MDCS)

> Run Maven commands from the project root: `mdcs-forge/`

---

## Project Structure
MDCS currently contains two Maven modules:

```text
mdcs/
├── pom.xml
├── shared/
│   └── pom.xml
└── apps/
    └── core/
        └── pom.xml
```

The dependency order is:

```text
shared → core
```

`shared` contains common models, networking, security, file I/O, and utilities.

`core` contains the MDCS Core application and depends on `shared`.

The Go server is a separate Go module and is **not built by Maven**.

---

# Clean Build

### `mvn clean`
Deletes previously generated Maven build outputs (`target/` directories) across the Maven modules.

Does not compile or package anything.

Useful when old build artifacts may be causing unexpected behavior.

---

### `mvn clean compile`
Cleans previous build outputs and compiles all Maven modules.

Java source files are compiled into:

```text
shared/target/classes/
apps/core/target/classes/
```

---

### `mvn clean install`
Performs a complete Maven build:
1. Cleans previous build outputs
2. Compiles all modules
3. Runs tests
4. Packages artifacts
5. Installs Maven artifacts into the local repository (`~/.m2/`)

Maven automatically builds modules in dependency order:

```text
shared → core
```

---

# Compile Only

### `mvn compile`
Compiles all Maven modules without packaging or installing them.

Generated `.class` files are placed inside each module's `target/classes/` directory.

---

### `mvn -pl shared compile`
Compiles only the `shared` module.

---

### `mvn -pl apps/core -am compile`
Compiles `core` and all Maven modules it depends on.

Since `core` depends on `shared`, Maven builds:

```text
shared → core
```

This is useful during Core development when you want to ensure its dependencies are compiled as well.

---

# Testing

### `mvn test`
Compiles the project and runs the available tests across the Maven modules.

Does not install artifacts into the local Maven repository.

---

### `mvn -pl apps/core test`
Runs tests for the Core module.

---

### `mvn -pl apps/core -am test`
Runs tests for Core and its required Maven dependencies.

---

# Packaging

### `mvn package`
Compiles, tests, and packages the Maven modules.

Generated artifacts are placed inside:

```text
shared/target/
apps/core/target/
```

---

### `mvn clean package`
Performs a clean build, compilation, testing, and packaging.

Unlike `install`, this does **not** install artifacts into the local Maven repository.

---

# Execution

## Core
The Core application uses:

```text
com.mdcs.core.Main
```

as its main entry point.

When running Core through Maven, use the Maven Exec Plugin if it is configured in the project:

```bash
mvn -pl apps/core exec:java -Dexec.mainClass="com.mdcs.core.Main"
```

For a clean build followed by execution:

```bash
mvn clean compile -pl apps/core exec:java -Dexec.mainClass="com.mdcs.core.Main"
```

> Core is currently the executable Java application in the repository. There is no `apps/cli` module in the current project structure.

---

# Module-Specific Build Control

### Build only Core
```bash
mvn -pl apps/core install
```

Builds the Core module.

If Core depends on a locally modified `shared` module, prefer:

```bash
mvn -pl apps/core -am install
```

This builds both:

```text
shared → core
```

---

### Build only Shared

```bash
mvn -pl shared install
```

---

# Debug / Validation

### `mvn dependency:tree`
Displays the Maven dependency tree.

Useful for investigating:
* Missing dependencies
* Unexpected transitive dependencies
* Dependency conflicts
* Dependency relationships between modules

---

### `mvn help:effective-pom`
Displays the effective POM after Maven resolves inheritance, properties, profiles, and other 
configuration.

Useful when Maven behaves differently from what the individual `pom.xml` files appear to specify.

---

# Fast Development Cycle
When actively working on Core:

```bash
mvn -pl apps/core -am compile
```

This compiles Core together with its Maven dependencies.

For changes only inside `shared`:

```bash
mvn -pl shared compile
```

For a complete verification before committing:

```bash
mvn clean test
```

---

# Go Server
The server is a separate Go module:

```text
server/
├── go.mod
├── go.sum
├── main.go
├── core/
├── data/
├── models/
├── modules/
└── tools/
```

Maven commands do **not** build or execute the server.

Run Go commands from:

```text
mdcs-forge/server/
```

### Build

```bash
go build ./...
```

### Test

```bash
go test ./...
```

### Run

```bash
go run .
```

---

# Golden Rules

* Run Maven commands from the project root:

```text
mdcs-forge/
```

* Run Go commands from:

```text
mdcs-forge/server/
```

* Maven manages the Java module dependency order automatically:

```text
shared → core
```

* Do not commit generated `target/` directories or compiled `.class` files.
* Use `-am` when working on a module together with its Maven dependencies.
* `server/` is independent of the Maven build.
* `apps/cli` is not part of the current project structure.

---

## Quick Reference

| Task                        | Command                         |
| --------------------------- | ------------------------------- |
| Clean                       | `mvn clean`                     |
| Compile everything          | `mvn compile`                   |
| Clean + compile             | `mvn clean compile`             |
| Test everything             | `mvn test`                      |
| Clean + test                | `mvn clean test`                |
| Package everything          | `mvn package`                   |
| Clean + package             | `mvn clean package`             |
| Full Maven build            | `mvn clean install`             |
| Compile Core + dependencies | `mvn -pl apps/core -am compile` |
| Test Core + dependencies    | `mvn -pl apps/core -am test`    |
| Build Core + dependencies   | `mvn -pl apps/core -am install` |
| Show dependencies           | `mvn dependency:tree`           |
| Show effective POM          | `mvn help:effective-pom`        |
| Build Go server             | `cd server && go build ./...`   |
| Test Go server              | `cd server && go test ./...`    |
| Run Go server               | `cd server && go run .`         |

---
