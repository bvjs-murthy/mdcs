"""
MDCS black-box integration / system test runner.

Entry point: argument parsing and config loading.
"""

import json, os, sys
from cli import fail
from utils import read_text
from suite import SuiteRunner

USAGE = """usage: runner.py [-h] [-v] json_path

Run MDCS module as a black-box integration/system test suite.

positional arguments:
  json_path      path to the test-suite JSON file

options:
  -h, --help     show this help message and exit
  -v, --verbose  print generated log paths
"""

def parse_args(argv):
    verbose, json_path, onlypos = False, None, False

    for arg in argv:

        if not onlypos and arg == "--": onlypos = True

        elif not onlypos and arg in ("-h", "--help"):
            print(USAGE)
            raise SystemExit(0)
        
        elif not onlypos and arg in ("-v", "--verbose"): verbose = True

        elif not onlypos and arg.startswith("-") and arg != "-":
            raise ValueError(f"unrecognized argument: {arg}")
        
        elif json_path is None: json_path = arg

        else: raise ValueError(f"unexpected extra argument: {arg}")
        
    if json_path is None:
        raise ValueError("the following arguments are required: json_path")
    
    return json_path, verbose

def load_config(path):
    data = json.loads(read_text(path))

    if isinstance(data, list):

        if len(data) != 1:
            raise ValueError("Top-level JSON array must contain exactly one suite")
        
        data = data[0]

    if not isinstance(data, dict):
        raise ValueError("Test configuration root must be a JSON object")
    
    return data

def main():

    try: json_path, verbose = parse_args(sys.argv[1:])

    except ValueError as exc: 
        sys.stderr.write(USAGE + f"error: {exc}\n")
        return 2

    path = os.path.realpath(json_path)

    if not os.path.exists(path):
        print(fail(f"Test file not found: {path}"), file=sys.stderr)
        return 2

    try: return SuiteRunner(load_config(path), path, verbose).run()

    except Exception as e:
        print(fail(f"Test runner failed: {type(e).__name__}: {e}"), file=sys.stderr)
        return 2

if __name__ == "__main__": raise SystemExit(main())
