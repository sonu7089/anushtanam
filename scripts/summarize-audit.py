"""Collect step-13 evidence without converting partial/failed runs into passes."""
import argparse
import hashlib
import importlib.util
import json
import re
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("perf_summary", ROOT / "scripts/summarize-perf.py")
perf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(perf)


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def read_log(path):
    data = path.read_bytes()
    return data.decode("utf-16" if data.startswith((b"\xff\xfe", b"\xfe\xff")) else "utf-8-sig")


def units():
    result = []
    for module, directory in [("domain", "core/domain/build/test-results/test"),
                              ("data", "core/data/build/test-results/testDebugUnitTest"),
                              ("app", "frontend/android/build/test-results/testDebugUnitTest")]:
        suites = [ET.parse(p).getroot() for p in (ROOT / directory).glob("TEST-*.xml")]
        assert suites, f"Missing unit reports: {module}"
        result.append({"module": module, **{key: sum(int(s.get(key, 0)) for s in suites)
                       for key in ("tests", "failures", "errors", "skipped")}})
    return result


def backend(raw):
    names = []
    runs = []
    for file in ("backend-all.log", "rules.log"):
        text = read_log(raw / file)
        def total(label):
            match = re.search(rf"{label} (\d+)\s*$", text, re.M)
            return int(match[1]) if match else None
        runs.append({"log": f"build/step13/{file}", "tests": total("tests"),
                     "passed": total("pass"), "failed": total("fail"), "skipped": total("skipped")})
        names.append(set(re.findall(r"^[✔√] (.+?) \([\d.]+ms\)\s*$", text, re.M)))
    return {"runs": runs, "distinctPassedTests": len(names[0] | names[1]),
            "additionalAuditProbes": len(names[1] - names[0])}


def instrumentation(path):
    if not path.exists():
        return {"status": "missing", "log": str(path.relative_to(ROOT))}
    text = read_log(path)
    passed = re.search(r"^OK \((\d+) tests?\)", text, re.M)
    duration = re.search(r"^Time: ([\d.]+)", text, re.M)
    return {"status": "passed" if passed else "failed_or_incomplete",
            "reportedTests": int(passed[1]) if passed else None,
            "seconds": float(duration[1]) if duration else None,
            "log": str(path.relative_to(ROOT)).replace("\\", "/")}


def run(directory):
    logs = sorted(directory.glob("instrumentation-*.log"))
    env = read(directory / "environment.json")
    measurements = {k.replace("\\", "/"): v for k, v in perf.summarize(directory).items()}
    if "completeHistorySamplesCombined" in measurements:
        measurements = {k: v for k, v in measurements.items() if not k.endswith("/history.json")}
    seen_benchmarks = []
    for key in list(measurements):
        if key.endswith("benchmarkData.json"):
            if measurements[key] in seen_benchmarks:
                del measurements[key]
            else:
                seen_benchmarks.append(measurements[key])
    expected_processes = env["iterations"] if env["freshHistoryProcesses"] else 1
    if env["historyOnly"]:
        histories = measurements.get("completeHistorySamplesCombined", {})
        sizes = ("0", "1000", "10000") if env["historySize"] == "all" else (env["historySize"],)
        enough_samples = all(histories.get(size, {}).get("wallMs", {}).get("n") == env["iterations"] for size in sizes)
    else:
        enough_samples = all(any(k.endswith(f"/startup-{mode}.json") and v.get("n") == env["iterations"]
                                 for k, v in measurements.items()) for mode in ("cold", "warm", "hot"))
        if env["accountState"] == "home":
            nav = next((v for k, v in measurements.items() if k.endswith("/navigation.json")), {})
            enough_samples &= all(nav.get(route, {}).get("n") == env["iterations"] for route in ("home", "progress", "settings"))
    return {"directory": str(directory.relative_to(ROOT)).replace("\\", "/"),
            "complete": len(logs) == expected_processes and enough_samples and
                        all(instrumentation(p)["status"] == "passed" for p in logs),
            "processes": len(logs), "expectedProcesses": expected_processes,
            "environment": {key: env.get(key) for key in ("fingerprint", "size", "density", "fontScale",
                                                         "animatorScale", "transitionScale", "windowScale", "timestamp")},
            "measurements": measurements}


def playback(path):
    result = []
    for sample in read(path)["samples"]:
        def delta(before, after):
            assert before["pid"] == after["pid"]
            return {"elapsedMs": after["elapsedMs"] - before["elapsedMs"],
                    "cpuMs": after["cpuMs"] - before["cpuMs"],
                    "counts": {k: after["counts"].get(k, 0) - before["counts"].get(k, 0)
                               for k in sorted(set(before["counts"]) | set(after["counts"]))
                               if k.startswith(("playback.", "render."))}}
        result.append({"playing": delta(sample["before"], sample["after"]),
                       "hidden": delta(sample["hiddenBefore"], sample["hiddenAfter"])})
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--app-run", type=Path, required=True)
    parser.add_argument("--history-retained", type=Path, required=True)
    parser.add_argument("--history-fresh", type=Path, required=True)
    parser.add_argument("--stopped-run", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "doc/perf/step-13-results.json")
    args = parser.parse_args()
    raw = ROOT / "build/step13"
    app = run(args.app_run.resolve())
    nav = next(v for k, v in app["measurements"].items() if k.endswith("/navigation.json"))
    library = next(v for k, v in app["measurements"].items() if k.endswith("benchmarkData.json"))
    frames = library["navigation"]["frameOverrunMs"]
    result = {
        "date": "2026-09-12", "rawDirectory": "build/step13",
        "scope": "Pixel 7 Pro plus Android emulator; local demo Firebase; no production deployment",
        "apkSha256": {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest() for path in (
            "frontend/android/build/outputs/apk/debug/app-debug.apk",
            "frontend/android/build/outputs/apk/benchmark/app-benchmark.apk",
            "core/data/build/outputs/apk/androidTest/debug/data-debug-androidTest.apk",
            "macrobenchmark/build/outputs/apk/benchmark/macrobenchmark-benchmark.apk")},
        "units": units(),
        "rulesSha256": hashlib.sha256((ROOT / "backend/firebase/firestore.rules").read_bytes()).hexdigest(),
        "backend": backend(raw),
        "instrumentation": {name: instrumentation(raw / path) for name, path in {
            "dataPixel": "data-device.log", "uiEmulator": "ui-emulator.log",
            "uiPixel": "ui-pixel.log", "cloudPixel": "recovery/cloud.log",
            "processDeathRecovery": "recovery/crash-recover.log",
            "pixelReceives": "recovery-final/primary-receives.log",
            "emulatorSends": "recovery-final/primary-receives-send.log",
            "emulatorReceives": "recovery-final/secondary-receives.log",
            "pixelSends": "recovery-final/secondary-receives-send.log",
            "playbackPixel": "playback-instrumentation.log"}.items()},
        "app": app,
        "historyRetained": run(args.history_retained.resolve()),
        "historyFresh": run(args.history_fresh.resolve()),
        "provisionalBudgets": {
            "cachedNavigationP95Ms": {"target": 250, "observed": {k: v["p95"] for k, v in nav.items()},
                                      "passed": all(v["p95"] <= 250 for v in nav.values())},
            "navigationPositiveOverrunPercent": {"targetLessThan": 5,
                "observed": frames["positiveOverrunPercent"], "passed": frames["positiveOverrunPercent"] < 5}},
        "limitations": [
            "Two physical phones, stable OS and lower-end hardware are not covered.",
            "Returning-account fixture was not fully controlled in the original baseline.",
            "Room history timings are debug analytics, not optimized navigation at those sizes.",
            "The broad account/slow-network/scroll release matrix and app-owned Baseline Profile remain open.",
            "The initial app run had a cold readiness timeout; preserve it separately from the final rerun."]}
    play = raw / "playback-results/playback.json"
    if play.exists():
        result["playback"] = playback(play)
    if args.stopped_run:
        result["closure"] = "Closed at the user's request with completed evidence and accepted remaining limitations."
        result["stoppedAtUserRequest"] = run(args.stopped_run.resolve())
        if not play.exists():
            result["instrumentation"]["playbackPixel"]["status"] = "not_run_user_closed_validation"
    comparisons = read(raw / "comparisons/results.json")
    result["artwork"] = {"comparisons": len(comparisons),
        "maxMeanChannelError": max(row["meanAbsoluteError"] for row in comparisons),
        "screenshots": len(list((raw / "screens").glob("*.png")))}
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(args.output)
