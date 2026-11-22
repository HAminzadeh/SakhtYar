from __future__ import annotations
import argparse
import runpy
import sys
from pathlib import Path
PACKAGE_ROOT = Path(__file__).resolve().parent
SOURCE_ROOT = PACKAGE_ROOT.parent
LEGACY_ROOT = PACKAGE_ROOT / "legacy"
if str(SOURCE_ROOT) not in sys.path:
    sys.path.insert(0, str(SOURCE_ROOT))
def run_legacy(name: str, args: list[str]) -> int:
    script = LEGACY_ROOT / name
    if not script.is_file():
        raise SystemExit(f"Runtime script not found: {script}")
    sys.argv = [str(script), *args]
    runpy.run_path(str(script), run_name="__main__")
    return 0
def smoke() -> int:
    from sakhtyar_persian.intake import DocumentIntakeEngine
    assert DocumentIntakeEngine is not None
    for name in ("dry_run_v07.py", "selected_execution_v010.py", "final_pipeline_v019.py"):
        if not (LEGACY_ROOT / name).is_file():
            raise SystemExit(f"Missing runtime: {name}")
    print("SAKHTYAR_PYTHON_SMOKE_OK")
    return 0
def main() -> int:
    parser = argparse.ArgumentParser(prog="sakhtyar-python")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("smoke")
    for command in ("classify", "selected-execution", "final-pipeline"):
        p = sub.add_parser(command); p.add_argument("args", nargs=argparse.REMAINDER)
    ns = parser.parse_args()
    if ns.command == "smoke": return smoke()
    mapping = {"classify":"dry_run_v07.py", "selected-execution":"selected_execution_v010.py", "final-pipeline":"final_pipeline_v019.py"}
    return run_legacy(mapping[ns.command], ns.args)
if __name__ == "__main__":
    raise SystemExit(main())