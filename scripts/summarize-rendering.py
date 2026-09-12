"""Summarize the step-12 ADB snapshots and standard Macrobenchmark JSON outputs."""
import hashlib
import json
import math
import re
from pathlib import Path
from statistics import median

ROOT = Path(__file__).resolve().parents[1]
RAW = ROOT / "build/step12"


def snapshot(name):
    line = next(line for line in (RAW / name).read_text(encoding="utf-8-sig").splitlines()
                if 'data="' in line)
    return json.loads(line.split('data="', 1)[1][:-1])


def delta(start, end):
    before, after = snapshot(start), snapshot(end)
    assert before["pid"] == after["pid"], "Comparison requires one process"
    return {
        "elapsedMs": after["elapsedMs"] - before["elapsedMs"],
        "cpuMs": after["cpuMs"] - before["cpuMs"],
        "counts": {key: after["counts"].get(key, 0) - before["counts"].get(key, 0)
                   for key in sorted(set(before["counts"]) | set(after["counts"]))
                   if key.startswith(("playback.", "render."))},
    }


def benchmarks(directory):
    data = json.loads((RAW / directory / "com.mantrasiddhi.macrobenchmark-benchmarkData.json").read_text())
    result = {}
    for benchmark in data["benchmarks"]:
        metrics = {}
        for name, metric in benchmark["sampledMetrics"].items():
            values = [value for run in metric["runs"] for value in run]
            metrics[name] = {key: metric[key] for key in ("P50", "P90", "P95", "P99")}
            metrics[name]["samples"] = len(values)
            if name == "frameOverrunMs":
                metrics[name]["positiveOverrunPercent"] = 100 * sum(v > 0 for v in values) / len(values)
        result[benchmark["name"]] = {"iterations": benchmark["repeatIterations"], "metrics": metrics}
    return data["context"], result


def shimmer_compositions(phase):
    text = (RAW / f"shimmer-{phase}-counts.txt").read_text(encoding="utf-8-sig")
    return int(re.findall(r"shimmer compositions over 60 frames=(\d+)", text)[-1])


context, before = benchmarks("traces-before")
_, after = benchmarks("traces-after")
result = {
    "date": "2026-09-12",
    "device": context["build"],
    "rawDirectory": "build/step12",
    "compilation": "None",
    "apkSha256": {name: hashlib.sha256((RAW / f"{name}.apk").read_bytes()).hexdigest()
                  for name in ("before", "after")},
    "playback": {name: delta(f"playback-{name}-start.txt", f"playback-{name}-end.txt")
                 for name in ("before", "after")},
    "hiddenPlayback": delta("playback-hidden-start.txt", "playback-hidden-end.txt"),
    "shimmer": {"frames": 60, "beforeCompositions": shimmer_compositions("before"),
                "afterCompositions": shimmer_compositions("after"),
                "beforeClocks": 16, "afterClocks": 1},
    "benchmarks": {"before": before, "after": after},
}
for phase in ("before", "after"):
    samples = json.loads((RAW / f"results-{phase}" / "navigation.json").read_text())["samples"]
    result["benchmarks"][phase]["tapToUsableMs"] = {}
    for route in sorted({sample["route"] for sample in samples}):
        values = sorted(sample["observedTapToUsableMs"] for sample in samples if sample["route"] == route)
        # Same nearest-rank convention as the baseline summary script.
        result["benchmarks"][phase]["tapToUsableMs"][route] = {
            "n": len(values), "median": median(values), "p95": values[math.ceil(len(values) * .95) - 1]}
output = ROOT / "doc/perf/step-12-results.json"
output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
print(output)
