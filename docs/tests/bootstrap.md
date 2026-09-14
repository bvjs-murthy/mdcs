# Bootstrap Test Cases

## 1. FILE SCHEMA TESTS
### A. Missing file

Delete:
```
Accounts.json
```

Expected:
- default file created
- bootstrap continues
- report added
- app_state = CONTINUE

### B. Invalid JSON format

Put garbage:
```
{ hello
```

Expected:
- INVALID_FILE_FORMAT
- recovery/default creation
- bootstrap continues OR terminates depending on recovery result

### C. Valid JSON, invalid schema

Example:
```
{
  "user_name": 123
}
```

Expected:
- INVALID_FILE_SCHEMA
- structured issue report

## 2. VERSION FORMAT TESTS

Change:
```
app.version=1.a.0
```

Expected:
- INVALID_VERSION_FORMAT
- app TERMINATE

## 3. UPDATE SERVER TESTS

### A. Server offline

Stop server.

Expected:
- UPDATE_CHECK_FAILED
- CONTINUE mode
- "Proceeding without update check"

### B. Invalid server JSON

Return:
```
{
  "random": true
}
```

Expected:
- INVALID_UPDATE_RESPONSE
- CONTINUE mode

### C. Critical update

Metadata:
```
"minimum_supported_version": "2.0.0"
```

Client:
```
1.0.0
```

Expected:
- BLOCK state
- CRITICAL_UPDATE
- update prompt

### D. Optional update

Metadata:
```
latest = 1.2.0
```

Client:
```
1.1.0
```

Expected:
- optional update available
- app still continues

## 4. PLUGIN TESTS

### A. Plugin update available

Metadata:
```
clipboard available = 1.1.0
```

Client:
```
clipboard = 1.0.0
```

Expected:
- update_required = true

### B. Plugin incompatible

Metadata:
```
"compatible_app_versions": {
  "min": "2.0.0",
  "max": "2.5.0"
}
```

Client app:
```
1.0.0
```

Expected:
- is_compatible = false

## 5. UNKNOWN PLUGIN TEST

Client sends:
```
"plugins": {
  "alien_plugin": "1.0.0"
}
```

Expected:
- ignored safely

## 6. EMPTY PLUGIN MAP
```
"plugins": {}
```

Expected:
- works normally

## 7. SEMVER EDGE TESTS

Test:
```
Current Compare	  Expected
2.0.0   1.9.9	    NOT lower
1.10.0	1.2.0	    NOT lower
1.0.9	  1.0.10	  lower
```

---