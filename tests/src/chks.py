"""
Everything that verifies a test case's outcome: comparison helpers,
HTTP server checks, file assertions and IPC assertions.
"""

import json, os
from utils import render, read_text
from http import http_request

def deep_equal(actual, expected, path="$"):
    
    if isinstance(expected, dict):

        if not isinstance(actual, dict):
            return [f"{path}: expected object, got {type(actual).__name__}"]
        
        errors = []

        for key, value in expected.items():

            if key not in actual: errors.append(f"{path}.{key}: missing")
            else: errors.extend(deep_equal(actual[key], value, f"{path}.{key}"))

        return errors
    
    if actual != expected:
        return [f"{path}: expected {expected!r}, got {actual!r}"]
    
    return []

def get_json_path(data, expression):
    expr = expression.strip()

    if expr.startswith("$."): expr = expr[2:]
    elif expr == "$": return True, data

    cur = data

    for part in expr.split("."):

        if isinstance(cur, dict) and part in cur:cur = cur[part]
        else: return False, None

    return True, cur

def http_check(spec, root, case_dir, app_data):
    method = str(spec.get("method", "GET")).upper()
    url = render(spec["url"], root, case_dir, app_data)
    body = spec.get("body")

    data = None
    headers = {
        str(k): str(v)
            for k, v in render(spec.get("headers", {}), root, case_dir, app_data).items()
    }

    if "raw_body" in spec:
        data = str(spec["raw_body"]).encode("utf-8")

    elif body is not None:
        data = json.dumps(body).encode("utf-8")

        if not any(k.lower() == "content-type" for k in headers):
            headers["Content-Type"] = "application/json"

    status, _resp_headers, raw = http_request(
        method, url, headers, data, float(spec.get("timeout", 5))
    )

    text = raw.decode("utf-8", errors="replace")
    expected = spec.get("expect", {})
    errors = []

    if "status" in expected and status != int(expected["status"]):
        errors.append(f"HTTP status: expected {expected['status']}, got {status}")

    if "contains" in expected and str(expected["contains"]) not in text:
        errors.append(f"body does not contain {expected['contains']!r}")

    if "json" in expected:

        try:
            actual_json = json.loads(text)

        except ValueError as exc:  # JSONDecodeError subclasses ValueError
            errors.append(f"response is not JSON: {exc}")

        else:
            errors.extend(deep_equal(actual_json, expected["json"]))

    return (not errors, "; ".join(errors) if errors else f"HTTP {status}")

def file_assertion(spec, root, case_dir, app_data):
    path = render(spec["path"], root, case_dir, app_data)
    errors = []

    exists = os.path.exists(path)

    if "exists" in spec and exists != bool(spec["exists"]):
        errors.append(f"{path}: exists={exists}, expected {spec['exists']}")

    if not exists: return errors

    actual = None

    if "json" in spec or "json_path" in spec:

        try: actual = json.loads(read_text(path))
        except Exception as exc: return [f"{path}: invalid JSON: {exc}"]

    if "json" in spec:
        errors.extend(deep_equal(actual, spec["json"]))

    if "json_path" in spec:

        for expression, expected in spec["json_path"].items():
            found, value = get_json_path(actual, expression)

            if not found:
                errors.append(f"{path}:{expression}: not found")

            elif value != expected:
                errors.append(f"{path}:{expression}: expected {expected!r}, got {value!r}")

    return errors

def evaluate_assertions(assertions, root, case_dir, app_data, events):
    errors = []

    for spec in assertions.get("files", []):
        errors.extend(file_assertion(spec, root, case_dir, app_data))

    for spec in assertions.get("ipc", []):
        matches = [
            e for e in events
            if e.service == spec.get("service", e.service)
            and e.action == spec.get("action", e.action)
        ]

        if "count" in spec and len(matches) != int(spec["count"]):
            errors.append(
                f"IPC {spec.get('service')}/{spec.get('action')}: "
                f"expected count {spec['count']}, got {len(matches)}"
            )

        if "payload_contains" in spec:
            needle = str(spec["payload_contains"])
            
            if not any(needle in e.payload.decode("utf-8", errors="replace") for e in matches):
                errors.append(
                    f"IPC {spec.get('service')}/{spec.get('action')}: "
                    f"no payload contained {needle!r}"
                )

    return errors
