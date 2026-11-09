# Auth System Design

## Goals
- Secure user authentication
- First device registration & verification
- Low latency

## Functionalities
- User signin/signup
- Password reset
- Workspace creation
- First device registration (automated)

## Core Entities
- User → authentication identity
- Workspace → logical container for devices
- Device → physical client instance
- Session → authenticated access state

## High Level Design
> Will be updated soon

## Flow

### Signup
- Key Variables:
    - email, username, password - user creds
    - device creds
        - device_name, workspace_name - for user identification of user devices and workspace
        - device_id, workspace_id - for system identification of user devices and workspace

1. Account Creation
```
User installs MDCS on first device → Opens app
    ↓
Selects: Signup
    ↓
Enters: email, username, password
    ↓
Frontend → Backend: create account request
    ↓
Backend:
    - validates input
    - creates user (status: UNVERIFIED)
    - sends OTP to email
    ↓
Frontend receives: "OTP required" state
```

2. Email Verification
```
User enters OTP
    ↓
Frontend → Backend: verify OTP
    ↓
Backend:
    - verifies OTP
    - marks user as VERIFIED
    - generates JWT (temporary session token)
    ↓
Backend → Frontend:
    - session JWT (active)
    - user_id
```

3. Workspace & First Device setup
```
Frontend inputs device creds [device_name, workspace_name]
    ↓
Frontend → Backend:
    - auth_token
    - workspace_name
    - device_name
    ↓
Backend:
    - creates workspace
    - assigns user as OWNER
    - registers device as PRIMARY_DEVICE
    - links device ↔ user ↔ workspace
    - generates device_id
    ↓
Backend → Frontend:
    - workspace_id
    - device_id
```

4. Client Finalization
```
Frontend:
    - stores:
        user_id
        device_id
        device_name
        workspace_id
        workspace_name
    - securely caches session JWT
    ↓
User enters main app
```

### Signin
1. User Authentication
```
User selects: Signin
    ↓
Enters: email, password
    ↓
Frontend → Backend: signin:
    - email
    - password
    - device_id (current device)
    ↓
Backend:
    - Verifies creds
```

2. Device Authentication (user auth passed)
- `Case A: Known device`
```
Backend: (if request from known device)
    - Returns:
        session JWT
        current device & registered devices details
        workspace details
```

- `Case B: Unknown device`
```
Backend: (Missing device id or not recognized)
    - flags device as UNVERIFED
    - triggers device registration (temporary pairing code)
    ↓
Frontend enters device registration flow
```

3. Client Finalization
```
Frontend:
    - stores access_token securely (if device is VERIFIED)
    - stores device_id, wokspace_id, user_id
    - if VERIFIED → go to app
    - if UNVERIFIED → trigger device linking flow
```

- Response Design:
```
{
    "access_token": "...",
    "user_id": "...",
    "device_id": "...",
    "workspace_id": "...",
    "device_status": "VERIFIED | UNVERIFIED"
}
```

### Device Registration

1. Initiating Pairing Key (Main device)
```
User selects: "Register Device"
    ↓
Main device → Backend:
    - request pairing_key (auth required)
    ↓
Backend:
    - generates pairing_key
    - binds it to:
        user_id
        workspace_id
        expiry (60s)
        status: ACTIVE
    ↓
Returns pairing_key
```

2. New Device Registration
```
User logs in on new device
    ↓
(Backend goes through `Signin step 2, case B`)
    ↓
New device:
    - enters device registration flow
```

3. Pairing Step
```
User enters pairing_key on new device
    - rate limited
    - 5 tries max
    ↓
New device → Backend:
    - pairing_key
    - device_id
```

4. Backend Verification
```
Backend:
    - validates pairing_key
        - exists
        - not expired
        - not used
    - fetches:
        - user_id, workspace_id from pairing_key
    - verifies user match
    - flags device as VERIFIED
    - updates devices in workspace under the user
    - marks pairing_key as USED
```

5. Final Response
```
Backend → New device:
    - user_id
    - device_id
    - workspace_id
    - session JWT
```

6. Completion
```
New device:
    - stores access_token + device_id
    ↓
User enters main app
```

## Known Limitations (v1.0.0)

- No device-level cryptographic identity
- No session revocation
- Limited protection against token theft

## Planned Improvements (v1.1.0)

- Device tokens
- Key-based authentication
- Stronger trust model

---