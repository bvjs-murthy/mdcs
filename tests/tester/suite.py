"""
Test suite execution: prerequisites, per-case isolation, running the
application under test, and reporting results.
"""

import json, os, subprocess, time

from tests.tester.chks import evaluate_assertions, http_check
from tests.tester.cli import ok, fail, warn, info, dim
from ipc import CoreIPC

from tests.tester.utils import (
    command_from, make_temp_dir, remove_tree, render, run_command,
    wait_for_port, write_text,
)

EXIT_CODES = {
    0: "SUCCESS", 1: "FAILURE", 2: "BAD_REQUEST", 3: "BAD_STATE",
    4: "NOT_FOUND", 5: "ALREADY_EXISTS", 6: "PERMISSION_DENIED",
    7: "UNAUTHORIZED", 8: "TIMEOUT", 9: "UNAVAILABLE", 10: "CONFLICT",
    11: "INTERRUPTED", 12: "CANCELLED", 13: "NOT_SUPPORTED",
    14: "RESOURCE_EXHAUSTED", 15: "DEPENDENCY_FAILURE", 16: "IO_ERROR",
    17: "CONFIGURATION_ERR", 18: "PROTOCOL_ERR", 19: "DATA_ERR",
    20: "INTERNAL_ERR", 21: "NOT_READY", 22: "BLOCKED", 23: "TERMINATED",
    24: "RESTART_REQUIRED",
}


class TestResult:
    def __init__(self, name, passed, elapsed, actual_exit=None,
                 expected_exit=None, exit_meaning="UNKNOWN", errors=None):
        self.name = name
        self.passed = passed
        self.elapsed = elapsed
        self.actual_exit = actual_exit
        self.expected_exit = expected_exit
        self.exit_meaning = exit_meaning
        self.errors = errors if errors is not None else []


class SuiteRunner:

    def run_prereqs(self):
        r = self.root

        for pre in self.config.get("prerequisites", []):
            print(info(f"\t- prerequisite: {pre.get('name', 'prerequisite')}"))

            if pre.get("command"):

                if pre.get("persistent"):
                    cmd = command_from(render(pre["command"], r, r, r))
                    cwd = render(pre.get("cwd", r), r, r, r)
                    env = os.environ.copy()

                    env.update({
                        str(k): str(v) for k, v in render(pre.get("env", {}), r, r, r).items()
                    })

                    os.makedirs(self.result_dir, exist_ok=True)
                    
                    self.ser_log_han = open(
                        os.path.join(self.result_dir, "server.log"), "w", encoding="utf-8"
                    )

                    self.ser_proc = subprocess.Popen(
                        cmd, cwd=cwd, env=env,
                        stdin=subprocess.DEVNULL,
                        stdout=self.ser_log_han,
                        stderr=subprocess.STDOUT,
                    )

                    ready = pre.get("ready")

                    if ready and ready.get("type") == "port":

                        wait_for_port(
                            ready.get("host", "127.0.0.1"),
                            int(ready["port"]),
                            float(ready.get("timeout", 10)),
                        )

                else: run_command(pre, r, r, r)

            if pre.get("wait"): time.sleep(float(pre["wait"]))

    def stop_prerequisites(self):
        if self.ser_proc is not None and self.ser_proc.poll() is None:
            self.ser_proc.terminate()
            try:
                self.ser_proc.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.ser_proc.kill()

        if self.ser_log_han is not None:
            self.ser_log_han.close()
            self.ser_log_han = None

    # ---- single case -----------------------------------------------------

    def run_case(self, case):
        name = case["name"]
        start = time.perf_counter()
        case_dir = make_temp_dir("mdcs-test-")
        app_data = os.path.join(case_dir, "config", "MDCS")
        os.makedirs(app_data, exist_ok=True)

        expected = case.get("expected", {})
        expected_exit = expected.get("exit_code")
        timeout = float(case.get("timeout", self.config.get("timeout", 20)))

        os.makedirs(self.result_dir, exist_ok=True)
        log_path = os.path.join(self.result_dir, f"{name}.log")

        errors = []
        process = None
        ipc = None

        try:
            # Per-case isolation: Core honors XDG_CONFIG_HOME on Linux.
            env = os.environ.copy()
            env["XDG_CONFIG_HOME"] = os.path.join(case_dir, "config")

            env.update({
                str(k): str(v)
                    for k, v in render(case.get("env", {}), self.root, case_dir, app_data).items()
            })

            for setup in case.get("setup", []): run_command(setup, self.root, case_dir, app_data)

            if case.get("process", True):
                app = case.get("application", self.config.get("application"))

                if not app: raise ValueError("No application definition found")

                application_path = render(app.get("path", ""), self.root, case_dir, app_data)
                command_template = command_from(case.get("command", app.get("command")))
                args_text = " ".join(str(x) for x in case.get("args", []))

                command = [
                    render(
                        x.replace("{application}", application_path).replace("{args}", args_text),
                        self.root,
                        case_dir,
                        app_data
                    ) for x in command_template
                ]

                cwd = render(
                    case.get("cwd", app.get("cwd", self.root)),
                    self.root,
                    case_dir,
                    app_data
                )

                with open(log_path, "w", encoding="utf-8") as log_file:
                    process = subprocess.Popen(
                        command, cwd=cwd, env=env,
                        stdin=subprocess.PIPE,
                        stdout=subprocess.PIPE,
                        stderr=subprocess.STDOUT,
                        bufsize=0,
                    )

                    inp = case.get("input", {})
                    responses = inp.get("responses", {}) if isinstance(inp, dict) else {}

                    ipc = CoreIPC(process, log_file, responses)
                    ipc.start()

                    try: process.wait(timeout=timeout)

                    except subprocess.TimeoutExpired:
                        errors.append(f"process exceeded timeout of {timeout:.2f}s")
                        process.kill()
                        process.wait(timeout=3)

                    ipc.stop()
                    ipc.join(1)

                    errors.extend(f"IPC: {x}" for x in ipc.protocol_errors)

                    actual_exit = process.returncode

                    if expected_exit is not None and actual_exit != int(expected_exit):
                        errors.append(
                            f"exit code: expected {expected_exit} "
                            f"({EXIT_CODES.get(int(expected_exit), 'UNKNOWN')}), "
                            f"got {actual_exit} ({EXIT_CODES.get(actual_exit, 'UNKNOWN')})"
                        )

                    errors.extend(evaluate_assertions(
                        expected.get("assert", {}),
                        self.root, case_dir, app_data, ipc.events))
            else:
                write_text(
                    log_path,
                    f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] "
                    "[HARNESS] process execution disabled; HTTP/server checks only.\n")

            for check in case.get("server_checks", []):
                passed, message = http_check(check, self.root, case_dir, app_data)
                if not passed:
                    errors.append(f"server: {message}")

            for cleanup in case.get("cleanup", []):
                run_command(cleanup, self.root, case_dir, app_data, check=True)

        except Exception as exc:
            errors.append(f"harness error: {type(exc).__name__}: {exc}")

        finally:
            if process is not None and process.poll() is None:
                process.kill()
                process.wait()
            remove_tree(case_dir)

        elapsed = time.perf_counter() - start
        actual_exit = process.returncode if process is not None else None

        return TestResult(
            name=name,
            passed=not errors,
            elapsed=elapsed,
            actual_exit=actual_exit,
            expected_exit=int(expected_exit) if expected_exit is not None else None,
            exit_meaning=EXIT_CODES.get(actual_exit, "UNKNOWN"),
            errors=errors,
        )

    def run(self):
        suite_name = self.config.get("test_name", self.config.get("name", "MDCS Test Suite"))

        print(f'\n{info(f"MDCS :: {suite_name}")}')
        print(dim("-" * 72))

        try:
            self.run_prereqs()
            cases = self.config.get("cases", [])

            if not cases: raise ValueError("No test cases found")

            for index, case in enumerate(cases, start=1):
                
                if str(case.get("status", "")).lower() in ("wip", "skip", "skipped"):
                    print(f"[{index:02d}/{len(cases):02d}] {case['name']} ... " + warn("SKIP"))
                    continue

                print(f"[{index:02d}/{len(cases):02d}] {case['name']} ...", end="", flush=True)
                result = self.run_case(case)
                self.results.append(result)

                if result.passed:
                    print(" " + ok("PASS") + f"  {result.elapsed:.3f}s"
                          f"  exit={result.actual_exit} {result.exit_meaning}")
                else:
                    print(" " + fail("FAIL") + f"  {result.elapsed:.3f}s")
                    for error in result.errors:
                        print("       " + fail("↳ " + error))

                if self.verbose:
                    print("       " + dim("log: " + os.path.join(self.result_dir, f"{case['name']}.log")))
        finally:
            self.stop_prerequisites()

        return self._report(suite_name)

    def _report(self, suite_name):
        passed = sum(1 for r in self.results if r.passed)
        failed = len(self.results) - passed
        total_time = sum(r.elapsed for r in self.results)

        print(dim("─" * 72))
        print(f"Tests: {len(self.results)}   " + ok(f"Passed: {passed}") + "   "
              + (fail(f"Failed: {failed}") if failed else f"Failed: {failed}")
              + f"   Time: {total_time:.3f}s")

        summary_path = os.path.join(self.result_dir, "summary.json")
        os.makedirs(self.result_dir, exist_ok=True)
        write_text(summary_path, json.dumps({
            "suite": suite_name,
            "passed": passed,
            "failed": failed,
            "total": len(self.results),
            "time_seconds": round(total_time, 6),
            "tests": [
                {
                    "name": r.name,
                    "passed": r.passed,
                    "elapsed_seconds": round(r.elapsed, 6),
                    "actual_exit_code": r.actual_exit,
                    "actual_exit_meaning": r.exit_meaning,
                    "expected_exit_code": r.expected_exit,
                    "errors": r.errors,
                }
                for r in self.results
            ],
        }, indent=2))

        print("Result: " + (ok("PASS") if failed == 0 else fail("FAIL")) + f"  {dim(summary_path)}")
        print()

        return 0 if failed == 0 else 1

    def __init__(self, config, conf_path, verbose=False):
        self.config = config
        self.conf_path = conf_path
        
        # All paths resolve from the directory where the runner is invoked.
        self.root = os.path.realpath(os.getcwd())
        self.result_dir = os.path.join(self.root, "results", int(time.time()))
        self.verbose = verbose
        self.results = []
        self.ser_proc = None
        self.ser_log_han = None
