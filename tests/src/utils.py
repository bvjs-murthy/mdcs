import os, socket, subprocess, time, sys, shlex

def log(level: str, msg: str):
    cur = time.strftime("%Y-%m-%d %H:%M:%S", time.localtime())
    ms = int((time.time() % 1) * 1000)
    tstamp = f"{cur}.{ms:03d}"

    entry = f"{tstamp} | {level}: {msg}"

    sys.stderr.write(entry)

def render(value: any, root: str, case_dir: str, app_data: str) -> any:

    if isinstance(value, str):
        return (
            value.replace("{root}", root)
                .replace("{case_dir}", case_dir)
                .replace("{app_data}", app_data)
                .replace("{sep}", os.pathsep)
        )
    
    if isinstance(value, list):
        return [render(x, root, case_dir, app_data) for x in value]
    
    if isinstance(value, dict):
        return {k: render(v, root, case_dir, app_data) for k, v in value.items()}
    
    return value

def command_from(spec: any) -> any:

    if isinstance(spec, str): return shlex.split(spec)
    
    if isinstance(spec, list): return [str(x) for x in spec]

    raise ValueError(f"Invalid command: {spec!r}")

def run_command(spec: dict, root: str, case_dir: str, app_data: str, check=True):
    cmd = command_from(render(spec["command"], root, case_dir, app_data))
    cwd = render(spec.get("cwd", root), root, case_dir, app_data)
    env = os.environ.copy()

    env.update({
        str(k): str(v)
            for k, v in render(spec.get("env", {}), root, case_dir, app_data).items()
    })

    result = subprocess.run(cmd, cwd=cwd, env=env, text=True, capture_output=True)

    if check and result.returncode != 0:

        raise RuntimeError(
            f"Prerequisite command failed ({result.returncode}): {' '.join(cmd)}\n"
            f"{result.stdout}\n{result.stderr}"
        )
    
    return result

def wait_for_port(host, port, timeout):
    deadline = time.monotonic() + timeout

    while time.monotonic() < deadline:

        try:
            with socket.create_connection((host, port), timeout=0.4):
                return
            
        except OSError: time.sleep(0.15)

    raise TimeoutError(f"{host}:{port} did not become ready in {timeout:.1f}s")

_tmp_counter = 0

def make_temp_dir(prefix):
    global _tmp_counter
    base = os.environ.get("TMPDIR") or os.environ.get("TEMP") or os.environ.get("TMP") or "/tmp"
    os.makedirs(base, exist_ok=True)

    while True:
        _tmp_counter += 1
        path = os.path.join(base, f"{prefix}{os.getpid()}-{_tmp_counter}-{time.time_ns()}")

        try:
            os.makedirs(path)
            return path
        
        except FileExistsError: continue

def remove_tree(path):

    if not os.path.lexists(path): return

    if os.path.islink(path) or not os.path.isdir(path):

        try: os.remove(path)
        except OSError: pass

        return
    
    for dirpath, dirnames, filenames in os.walk(path, topdown=False):

        for name in filenames:
            try: os.remove(os.path.join(dirpath, name))
            except OSError: pass

        for name in dirnames:
            full = os.path.join(dirpath, name)

            try:
                if os.path.islink(full): os.remove(full)
                else: os.rmdir(full)

            except OSError: pass

    try: os.rmdir(path)
    except OSError: pass

def read_text(path):

    with open(path, "r", encoding="utf-8") as f: return f.read()

def write_text(path, text):

    with open(path, "w", encoding="utf-8") as f: f.write(text)
