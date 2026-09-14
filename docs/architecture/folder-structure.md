# MDCS Folder Structure
> Changes are possible as the project evolves

---

```
mdcs-forge/
├── apps/
│   └── core/
│       ├── pom.xml
│       └── src/main/
│           ├── java/com/mdcs/core/
│           │   ├── Main.java
│           │   ├── App.java
│           │   ├── Stream.java
│           │   ├── auth/
│           │   │   ├── Callbacks.java
│           │   │   ├── Enroll.java
│           │   │   ├── Login.java
│           │   │   └── Register.java
│           │   └── bootstrap/
│           │       ├── Schema.java
│           │       ├── Supervise.java
│           │       ├── UserState.java
│           │       └── Version.java
│           └── resources/
│               ├── application.properties
│               └── versions.properties
│
├── shared/
│   ├── pom.xml
│   └── src/main/java/com/mdcs/shared/
│       ├── fileio/
│       │   ├── DataClasses.java
│       │   └── FileIO.java
│       ├── logger/
│       │   └── Log.java
│       ├── models/
│       │   ├── Report.java
│       │   ├── State.java
│       │   ├── auth/
│       │   │   └── Network.java
│       │   ├── bootstrap/
│       │   │   └── Network.java
│       │   └── network/
│       │       └── Http.java
│       ├── network/
│       │   └── ProtoMet.java
│       ├── security/
│       │   ├── KeyManager.java
│       │   └── TokCipher.java
│       └── utils/
│           ├── NetErrors.java
│           └── SystemUtils.java
│
├── server/
│   ├── go.mod
│   ├── go.sum
│   ├── main.go
│   ├── core/
│   │   └── bootstrap/
│   │       ├── env.go
│   │       └── handler.go
│   ├── data/
│   │   ├── db.go
│   │   ├── ddl.sql
│   │   └── metadata.go
│   ├── models/
│   │   ├── metadata.go
│   │   └── schemas.go
│   ├── modules/
│   │   ├── router.go
│   │   ├── shared/
│   │   │   ├── models.go
│   │   │   └── util.go
│   │   ├── auth/
│   │   │   ├── controller.go
│   │   │   ├── middleware.go
│   │   │   ├── models.go
│   │   │   ├── routes.go
│   │   │   └── services.go
│   │   └── version/
│   │       ├── controller.go
│   │       ├── models.go
│   │       ├── routes.go
│   │       └── services.go
│   └── tools/
│       ├── mail.go
│       ├── auth/
│       │   └── usr.go
│       └── version/
│           └── version.go
│
├── docs/
│   ├── architecture/
│   │   ├── design.md
│   │   ├── folder-structure.md
│   │   └── overview.md
│   ├── development/
│   │   └── build-commands.md
│   ├── modules/
│   │   ├── feature.md
│   │   └── internal.md
│   ├── overview/
│   │   ├── features.md
│   │   └── introduction.md
│   ├── system-design/
│   │   ├── app-bootstrap.md
│   │   ├── auth-system.md
│   │   ├── standards.md
│   │   └── state-store.md
│   ├── tests/
│   │   └── bootstrap.md
│   └── assets/
│       └── images/
│           ├── distributed.webp
│           ├── master-worker.webp
│           ├── stream.webp
│           └── user_level_viz.webp
│
├── README.md
└── pom.xml
```

---
