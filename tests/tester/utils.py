"""
Small stdlib replacements and shared helpers:
command splitting, temp dirs, file IO, placeholder rendering, running commands.
"""

import os
import socket
import subprocess
import time


# --------------------------------------------------------------------------
# Command parsing / execution
# --------------------------------------------------------------------------

def split_command(text):
    """
    Minimal shlex.split replacement (quotes + backslash escapes, no shell).
    """

    args, cur, quote, has_token, i = [], [], None, False, 0

    while i < len(text):
        c = text[i]

        if quote:

            if c == quote: quote = None

            elif c == "\\" and quote == '"' and i + 1 < len(text) and text[i + 1] in '"\\':
                i += 1
                cur.append(text[i])

            else: cur.append(c)

        elif c in "'\"": quote, has_token = c, True

        elif c == "\\" and i + 1 < len(text):
            i += 1
            cur.append(text[i])
            has_token = True

        elif c in " \t\r\n":

            if has_token or cur:
                args.append("".join(cur))
                cur, has_token = [], False

        else:
            cur.append(c)
            has_token = True

        i += 1

    if quote: raise ValueError(f"Unterminated quote in command: {text!r}")

    if has_token or cur: args.append("".join(cur))

    return args

def render(value, root, case_dir, app_data):
    """
    Recursively expand {root} {case_dir} {app_data} {sep} in JSON values.
    """

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

def command_from(spec):

    if isinstance(spec, str): return split_command(spec)  # no shell=True on purpose
    
    if isinstance(spec, list): return [str(x) for x in spec]

    raise ValueError(f"Invalid command: {spec!r}")

def run_command(spec, root, case_dir, app_data, check=True):
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
            f"prerequisite command failed ({result.returncode}): {' '.join(cmd)}\n"
            f"{result.stdout}\n{result.stderr}"
        )
    
    return result

def wait_for_port(host, port, timeout):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection((host, port), timeout=0.4):
                return
        except OSError:
            time.sleep(0.15)
    raise TimeoutError(f"{host}:{port} did not become ready in {timeout:.1f}s")


# --------------------------------------------------------------------------
# Filesystem
# --------------------------------------------------------------------------

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
    """shutil.rmtree(ignore_errors=True) replacement."""
    if not os.path.lexists(path):
        return
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
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def write_text(path, text):
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
