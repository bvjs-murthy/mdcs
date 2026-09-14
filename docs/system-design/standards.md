# Standards
These are intended to be followed across the project, for any module.

## Exit Codes
| Code | Status | Meaning |
|------|--------|---------|
| 0 | SUCCESS | Operation completed successfully. |
| 1 | FAILURE | Operation could not be completed due to a general failure. |
| 2 | BAD_REQUEST | Supplied arguments or command structure are invalid. |
| 3 | BAD_STATE | Operation cannot be performed because the current state is invalid. |
| 4 | NOT_FOUND | Required resource, object, or entity was not found. |
| 5 | ALREADY_EXISTS | Requested resource, object, or entity already exists. |
| 6 | PERMISSION_DENIED | Operation was understood but is not permitted. |
| 7 | UNAUTHORIZED | Required authentication or authorization information is missing or invalid. |
| 8 | TIMEOUT | Operation did not complete within the expected time. |
| 9 | UNAVAILABLE | Required service, resource, or dependency is currently unavailable. |
| 10 | CONFLICT | Operation could not complete because it conflicts with the current state or another operation. |
| 11 | INTERRUPTED | Operation was interrupted before completion. |
| 12 | CANCELLED | Operation was explicitly cancelled before completion. |
| 13 | NOT_SUPPORTED | Requested operation is not supported. |
| 14 | RESOURCE_EXHAUSTED | Required system resource is unavailable or exhausted. |
| 15 | DEPENDENCY_FAILURE | A required dependency failed, preventing completion. |
| 16 | IO_ERROR | Input/output operation failed. |
| 17 | CONFIGURATION_ERR | Required configuration is missing, invalid, or inconsistent. |
| 18 | PROTOCOL_ERR | Communication or protocol requirements were violated. |
| 19 | DATA_ERR | Required data is malformed, corrupted, or otherwise invalid. |
| 20 | INTERNAL_ERR | Unexpected internal error occurred. |
| 21 | NOT_READY | Required component or system has not reached a usable state. |
| 22 | BLOCKED | Execution cannot continue and should remain blocked. |
| 23 | TERMINATED | Execution was intentionally terminated. |
| 24 | RESTART_REQUIRED | Operation cannot continue without restarting the component/process. |

---
