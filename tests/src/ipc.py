"""
MDCS Stream protocol: reads module's requests from stdout, answers requests on stdin using canned
responses from the test case.
"""

import json, threading

class IPCEvent:

    def __init__(self, id, service, action, headers, payload):
        self.id = id
        self.service = service
        self.action = action
        self.headers = headers
        self.payload = payload

class IPC:
    """
    Speaks the MDCS Stream protocol.

    Request:  <ID> <SERVICE> <ACTION>\\n [Header-Count]\\n Key: Value\\n [Length]\\n<Payload>\\n
    Response: <ID> <STATUS> <STATUS_CODE>\\n [Header-Count]\\n Key: Value\\n [Length]\\n
        <Payload>\\n
    """

    def start(self): self._thread.start()
    def stop(self): self._stop.set()
    def join(self, timeout): self._thread.join(timeout=timeout)

    def _readline(self):
        line = self.process.stdout.readline()

        if not line: raise EOFError("Stdout closed")

        return line.rstrip(b"\r\n")

    def _read_exact(self, n):
        data = bytearray()

        while len(data) < n:
            chunk = self.process.stdout.read(n - len(data))

            if not chunk:
                raise EOFError(f"Stdout closed while reading {n} payload bytes")
            
            data.extend(chunk)

        return bytes(data)

    @staticmethod
    def _parse_count(line):
        text = line.decode("ascii")

        if not (text.startswith("[") and text.endswith("]")):
            raise ValueError(f"Invalid count line: {text!r}")
        
        return int(text[1:-1])

    def _read_message(self):
        status = self._readline().decode("utf-8")
        parts = status.split(" ", 2)

        if len(parts) != 3: raise ValueError(f"Invalid status/request line: {status!r}")

        msg_id, service, action = int(parts[0]), parts[1], parts[2]

        header_count = self._parse_count(self._readline())
        headers = {}

        for _ in range(header_count):
            raw = self._readline().decode("utf-8")

            if ":" not in raw: raise ValueError(f"Invalid header: {raw!r}")

            key, value = raw.split(":", 1)
            headers[key.strip()] = value.strip()

        payload = self._read_exact(self._parse_count(self._readline()))
        delimiter = self.process.stdout.read(1)

        if delimiter not in (b"\n", b"\r"):
            raise ValueError(f"Expected message delimiter, got {delimiter!r}")

        return IPCEvent(msg_id, service, action, headers, payload)

    def _write_response(self, event, response):

        if isinstance(response, dict):
            payload = response.get("payload", "")
            status = str(response.get("status", "OK"))
            status_code = int(response.get("status_code", 0))
            headers = {str(k): str(v) for k, v in response.get("headers", {}).items()}

        else: payload, status, status_code, headers = response, "OK", 0, {}

        if isinstance(payload, (dict, list)):
            payload_bytes = json.dumps(payload).encode("utf-8")

        elif payload is None: payload_bytes = b""

        else: payload_bytes = str(payload).encode("utf-8")

        out = bytearray()
        out += f"{event.id} {status} {status_code}\n".encode("utf-8")
        out += f"[{len(headers)}]\n".encode("utf-8")

        for key, value in headers.items(): out += f"{key}: {value}\n".encode("utf-8")

        out += f"[{len(payload_bytes)}]".encode("ascii")
        out += payload_bytes
        out += b"\n"

        self.process.stdin.write(out)
        self.process.stdin.flush()

    def _find_response(self, event):
        # A list is consumed one item per occurrence of the same action.

        if event.action not in self.responses:
            raise KeyError(
                f"No test response configured for request "
                f"{event.service}/{event.action} (id={event.id})"
            )
        
        value = self.responses[event.action]

        if isinstance(value, list):

            if not value:
                raise KeyError(f"No remaining responses for action {event.action}")
            
            return value.pop(0)
        
        return value

    def _read_loop(self):

        try:
            while not self._stop.is_set():
                event = self._read_message()
                self.events.append(event)
                self._write_response(event, self._find_response(event))

        except EOFError:

            if self.process.poll() is None:
                self.protocol_errors.append("Stdout closed before process exit")

        except (ValueError, UnicodeError, OSError, KeyError) as exc:
            self.protocol_errors.append(str(exc))

            # Closing stdin wakes a module blocked waiting for a manager response.
            try: self.process.stdin.close()
            except OSError: pass

    def __init__(self, process, log_file, responses):
        self.process = process
        self.log_file = log_file
        self.responses = responses
        self.events = []
        self.protocol_errors = []
        self._stop = threading.Event()
        self._thread = threading.Thread(target=self._read_loop, daemon=True)
