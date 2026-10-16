import socket, time

def _dechunk(data):
    out = bytearray()

    while data:
        line, _, rest = data.partition(b"\r\n")

        try: size = int(line.split(b";")[0].strip(), 16)
        except ValueError: break
        
        if size == 0: break

        out += rest[:size]
        data = rest[size + 2:]

    return bytes(out)

def http_request(method, url, headers, data, timeout):
    
    if not url.startswith("http://"):
        raise ValueError(f"only http:// URLs are supported: {url!r}")

    rest = url[len("http://"):]
    hostport, _, path = rest.partition("/")
    path = "/" + path
    host, _, port_text = hostport.partition(":")
    port = int(port_text) if port_text else 80

    hdrs = {"Host": hostport, "Connection": "close"}
    hdrs.update(headers)

    if data is not None: hdrs["Content-Length"] = str(len(data))

    req = f"{method} {path} HTTP/1.0\r\n"
    req += "".join(f"{k}: {v}\r\n" for k, v in hdrs.items()) + "\r\n"
    payload = req.encode("utf-8") + (data or b"")

    deadline = time.monotonic() + timeout

    with socket.create_connection((host, port), timeout=timeout) as sock:
        sock.sendall(payload)
        raw = bytearray()

        while True:
            remaining = deadline - time.monotonic()

            if remaining <= 0:
                raise TimeoutError(f"HTTP request to {url} timed out")
            
            sock.settimeout(remaining)
            chunk = sock.recv(65536)

            if not chunk: break

            raw += chunk

    head, sep, body = bytes(raw).partition(b"\r\n\r\n")

    if not sep: raise ValueError("malformed HTTP response")

    lines = head.decode("iso-8859-1").split("\r\n")
    status = int(lines[0].split(" ", 2)[1])
    resp_headers = {}

    for line in lines[1:]:
        
        if ":" in line:
            k, v = line.split(":", 1)
            resp_headers[k.strip()] = v.strip()

    if resp_headers.get("Transfer-Encoding", "").lower() == "chunked":
        body = _dechunk(body)
        
    return status, resp_headers, body
