"""Summarize paired layout-fixture captures; never label emulator results as device results."""
import argparse
import json
import math
from pathlib import Path
from statistics import median


def summarize(directory):
    env = json.loads((directory / "environment.json").read_text(encoding="utf-8-sig"))
    if env["traceCompositions"]:
        raise ValueError("Composition-attribution runs must not be used as performance comparisons")
    benchmark_file = directory / "traces/com.mantrasiddhi.macrobenchmark-benchmarkData.json"
    benchmark_data = json.loads(benchmark_file.read_text(encoding="utf-8-sig"))
    benchmark = next(b for b in benchmark_data["benchmarks"] if b["name"] == "fixtureTabs")
    metrics = {}
    for name, metric in benchmark["sampledMetrics"].items():
        values = [value for run in metric["runs"] for value in run]
        if not values:
            continue
        metrics[name] = {key: metric[key] for key in ("P50", "P90", "P95", "P99")}
        metrics[name]["samples"] = len(values)
        if name == "frameOverrunMs":
            metrics[name]["positiveOverrunPercent"] = 100 * sum(v > 0 for v in values) / len(values)
    readiness_file = next((directory / "readiness").rglob("navigation.json"))
    samples = json.loads(readiness_file.read_text())["samples"]
    readiness = {}
    for route in sorted({sample["route"] for sample in samples}):
        values = sorted(sample["observedTapToUsableMs"] for sample in samples if sample["route"] == route)
        readiness[route] = {"n": len(values), "medianMs": median(values),
                            "p95Ms": values[math.ceil(len(values) * .95) - 1]}
    return {
        "rawDirectory": str(directory.as_posix()),
        "environment": {key: env[key] for key in ("fixture", "emulator", "fingerprint", "size", "density",
                                                    "fontScale", "animatorScale", "compilation", "graphics")},
        "apkSha256": json.loads((directory / "apk-hash.json").read_text(encoding="utf-8-sig"))["Hash"].lower(),
        "iterations": benchmark["repeatIterations"], "metrics": metrics, "observedTapToUsable": readiness,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("before", type=Path)
    parser.add_argument("after", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    before, after = summarize(args.before), summarize(args.after)
    if before["environment"] != after["environment"]:
        raise ValueError("Compare captures with the same environment and compilation settings")
    result = {"scope": "Fixed-data UI layout fixture; excludes account loading and speech readiness",
              "before": before, "after": after}
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(args.output)


if __name__ == "__main__":
    main()
