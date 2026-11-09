# MDCS Architecture

> **Status:** Draft  
> This document describes the high-level architecture of MDCS. It is intended to evolve as the
project evolves.

---

# Goals

## Primary Goals
* Synchronize data between user devices.
* Run in the background without requiring an open UI.
* Support both CLI and GUI frontends.
* Support plugins.
* Keep modules loosely coupled.
* Make each module responsible for one concern.

---

# Architecture Overview

```text
                   +------------------+
                   |     Manager      |
                   +------------------+
                            |
         ------------------------------------------
         |                  |                     |
         v                  v                     v
     +--------+      +-------------+      +-------------+
     |  Core  |      | ServiceHost |      | ServiceClient|
     +--------+      +-------------+      +-------------+
         |
         |
         +----------------+
         |                |
         v                v
      +------+       +----------+
      | CLI  |       |   GUI    |
      +------+       +----------+

Plugins communicate with Host/Client through the command router.
```

---

# Module Responsibilities

## Manager

### Purpose
Responsible for application lifecycle.

### Responsibilities
* Start automatically with the operating system.
* Launch required modules.
* Receive status reports from Core.
* Decide whether UI should be launched.
* Start or stop background services.
* Gracefully terminate all modules.

### Does NOT
* Authenticate users.
* Handle networking.
* Process business logic.

---

## Core

### Purpose
Prepare the application for execution.

### Responsibilities
* Bootstrap.
* Authentication.
* User state recovery.
* Device state recovery.
* Return startup status to Manager.

### Possible Results
* SUCCESS
* AUTH_REQUIRED
* TERMINATE
* RECOVER

---

## Service Host

### Purpose
Acts as the local service responsible for accepting requests from plugins and other modules.

### Responsibilities
* Accept commands.
* Broadcast updates.
* Coordinate synchronization.
* Maintain runtime state.

---

## Service Client

### Purpose
Communicates with remote devices.

### Responsibilities
* Receive synchronized data.
* Send local updates.
* Maintain network communication.

---

## Command Router

### Purpose
Routes commands between modules without tight coupling.

### Responsibilities
* Register handlers.
* Dispatch commands.
* Decouple modules.

---

## CLI

### Purpose
Terminal interface.

### Responsibilities
* Receive user input.
* Display responses.
* Invoke Core workflows when required.

---

## GUI

### Purpose
Graphical interface.

### Responsibilities
* Display application state.
* Collect user input.
* Trigger workflows.

---

## Plugins

### Purpose
Extend MDCS functionality.

### Responsibilities
* Produce events.
* Consume services exposed by Host.

Plugins must never directly communicate with Core.

---

# Startup Flow

```text
System Boot
      |
      v
Manager Starts
      |
      v
Launch Core
      |
      v
Bootstrap
      |
      v
Authentication
      |
      +-----------------------------+
      |                             |
      | Auth Required               |
      |                             |
      v                             v
Launch UI                     Success
      |                             |
      +-------------+---------------+
                    |
                    v
Launch Host
Launch Client
Launch Plugins
                    |
                    v
Runtime Ready
```

---

# Communication Rules
* Manager controls module lifecycle.
* Core reports status only.
* UI never launches services directly.
* Plugins communicate through the Host.
* Modules should communicate using interfaces rather than direct dependencies whenever possible.

---

# Design Principles
* One responsibility per module.
* Prefer composition over coupling.
* Separate lifecycle management from business logic.
* Keep interfaces stable.
* Delay optimization until required.
* Introduce new services only when an independent lifecycle is necessary.

---

# Future Improvements

## Planned
* Plugin sandbox.
* Automatic updates.
* Multi-account support.
* Service discovery.
* IPC improvements.

## Under Investigation
* Process isolation.
* Distributed command routing.
* Cross-platform service manager.

---

# Open Questions
* Should Host and Client remain separate processes?
* How should plugins authenticate?
* Should command routing be centralized?
* How should Manager detect crashed modules?
* What IPC mechanism should be used?

---

# Revision History

| Version | Date          | Changes                       |
| ------- | ------------- | ----------------------------- |
| 0.1     | Initial draft | Initial architecture document |

---
