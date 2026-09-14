# Internal Modules (Dependencies)
These modules will be automatically downloaded when user installs the application. These are
vitals for operation of the application.

## Manager
Orchastrates all the modules and services, maintains and watches application life-cycle.

## Core
### Bootstrap
Manage app launch protocols, Decides the next state of the application based on
- File system check
- Update check
- Version check

Finalizes the user state based on the checks and continues to next iternal library (auth).

### Auth
Control user registration, user login, device enrollment and access tokens validity. 

Finalizes the user state based on the auth status and state resolution.

## host 
User level local server that constantly listens for commands from other devices or from user and 
forwards to command router, which is an internal library which further sanitizes the command and
maps to srvices or plugins within the device.

## client 
Gets commands from services or plugins within the device and sanitizes them, forards to respective
device or broadcasts.

---