# App Bootstrap Process

### Phase 1: Load Application Metadata
- Read `application.properties` and `versions.properties`
- Validate:
    - Version format
    - Module version compatability

### Phase 2: Version & Update Check
- Try server version check
- cases:
    - Up-to-date &rarr; continue
    - Optional update &rarr; notify user
    - Mandatory update &rarr; block app until updated
    - Network failure &rarr; continue in offline mode

### Phase 3: Integrity & Schema Validation
For each file in `MDCS/`
- Check existence
- Validate format
- Validate schema
- If invalid:
    - Attempt recovery (last known good)

### Phase 4: User State Resolution
Based on Accounts.json:
- File missing &rarr; First Run
- Exists + valid:
    - login_status = false &rarr; Logged out
    - login_status = true &rarr; Logged in
- Invalid/corrupted &rarr; Logged out

Also:
- Validate `auth_token`
    - Else mark as corrupted

### Phase 5: Configuration Initialization
> Will b added for `GUI`
- If Configs.json missing:
    - Create with defaults
- If present:
    - Validate schema
    - Apply defaults for missing fields

### Phase 6: Device Context Setup
> Will be added after User & Device auth
- Load device.json
- Validate workspace/device binding
- If mismatch:
    - Re-sync with server OR
    - Reset device state

---