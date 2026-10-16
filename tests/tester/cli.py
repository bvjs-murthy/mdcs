"""
Terminal color helpers (plain ANSI instead of colorama).

Colors are only emitted when stdout is a TTY and NO_COLOR is not set.
"""

import os
import sys

if sys.stdout.isatty() and not os.environ.get("NO_COLOR"):

    if os.name == "nt":
        os.system("")  # enables ANSI escape processing on Windows 10+

    GREEN, RED, YELLOW, CYAN = "\033[32m", "\033[31m", "\033[33m", "\033[36m"
    DIM, RESET = "\033[2m", "\033[0m"

else: GREEN = RED = YELLOW = CYAN = DIM = RESET = ""

def ok(t): return f"{GREEN}{t}{RESET}"
def fail(t): return f"{RED}{t}{RESET}"
def warn(t): return f"{YELLOW}{t}{RESET}"
def info(t): return f"{CYAN}{t}{RESET}"
def dim(t): return f"{DIM}{t}{RESET}"
