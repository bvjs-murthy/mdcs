import json, os, sys
from utils import read_text, log
from suite import Suite

USAGE = """Run MDCS module as a black-box integration/system test suite.

usage: runner.py [-h] [-v] json_path

positional args:
    json_path      path to the test-suite JSON file

options:
    -h, --help     show this help message and exit
"""

def pargs(argv: list) -> str:
    json_path, onlypos = None, False

    for arg in argv:
        # For every arg, check if it is an option. If it is not any expected option, consider it
        # to be the json_path.
        
        if not onlypos and arg == "--": onlypos = True

        elif not onlypos and arg in ("-h", "--help"):
            sys.stdout.write(USAGE)
            raise SystemExit(0)

        elif not onlypos and arg.startswith("-") and arg != "-":
            raise ValueError(f"Unrecognized argument: {arg}")
        
        elif json_path is None: json_path = arg

        else: raise ValueError(f"Unexpected extra argument: {arg}")
        
    if json_path is None:
        raise ValueError("The following arguments are required: json_path")
    
    return json_path

def load_config(path: str) -> dict:
    data = json.loads(read_text(path))

    if isinstance(data, list):

        if len(data) != 1:
            raise ValueError("Top-level JSON array must contain exactly one suite")
        
        data = data[0]

    if not isinstance(data, dict):
        raise ValueError("Test configuration root must be a JSON object")
    
    return data

def main():

    try: json_path = pargs(sys.argv[1:])

    except ValueError as exc:
        log("Critical", exc)
        return 2

    path = os.path.realpath(json_path)

    if not os.path.exists(path):
        log("Critical", f"Test file not found: {path}\n")
        return 2

    try: return Suite(config=load_config(path), conf_path=path).run()

    except Exception as e:
        log("Critical", f"Failed to run test suite: {e}\n")
        return 2

if __name__ == "__main__": raise SystemExit(main())
