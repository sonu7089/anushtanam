"""Summarize baseline raw JSON without third-party dependencies (nearest-rank p95)."""
import argparse
import json
import math
from pathlib import Path
from statistics import median


def summary(values):
    ordered = sorted(values)
    if not ordered:
        return {"n": 0}
    return {"n": len(ordered), "median": round(median(ordered), 3),
            "p95": round(ordered[math.ceil(len(ordered) * .95) - 1], 3)}


def summarize(root):
    result = {}
    histories = {}
    for path in sorted(root.rglob("*.json")):
        data = json.loads(path.read_text(encoding="utf-8-sig"))
        if not isinstance(data, dict):
            continue
        key = str(path.relative_to(root))
        if "observedLaunchToUsableMs" in data:
            result[key] = summary(data["observedLaunchToUsableMs"])
        elif path.name == "navigation.json":
            result[key] = {route: summary([s["observedTapToUsableMs"] for s in data["samples"] if s["route"] == route])
                           for route in sorted({s["route"] for s in data["samples"]})}
        elif path.name == "history.json":
            result[key] = {"complete": data.get("complete", True), "histories": {str(f["sessions"]): {metric: summary([s[metric] for s in f["samples"]])
                           for metric in ("wallMs", "cpuMs", "selectQueries", "mappedSessionRows")}
                           for f in data["results"]}}
            if data.get("complete", True):
                for fixture in data["results"]:
                    histories.setdefault(str(fixture["sessions"]), []).extend(fixture["samples"])
        elif path.name == "idle.json":
            before, after = data["before"], data["after"]
            if before["pid"] != after["pid"]:
                raise ValueError("Idle sample crossed a process restart")
            result[key] = {"cpuPercentOfOneCore": data["cpuPercentOfOneCore"],
                           "active": after["active"], "workDelta": {
                               k: v - before["counts"].get(k, 0) for k, v in after["counts"].items()}}
        elif "benchmarks" in data:
            benchmarks = {}
            for benchmark in data["benchmarks"]:
                metrics = {m: summary(v["runs"]) for m, v in benchmark.get("metrics", {}).items() if "runs" in v}
                for metric, values in benchmark.get("sampledMetrics", {}).items():
                    frames = [frame for run in values["runs"] for frame in run]
                    metrics[metric] = summary(frames)
                    if metric == "frameOverrunMs" and frames:
                        metrics[metric]["positiveOverrunPercent"] = round(100 * sum(f > 0 for f in frames) / len(frames), 3)
                benchmarks[benchmark["name"]] = metrics
            result[key] = benchmarks
    # Combine fresh-process samples only within one runner environment, never a
    # parent folder containing different devices/builds/compilation experiments.
    if histories and (root / "environment.json").is_file():
        result["completeHistorySamplesCombined"] = {size: {
            metric: summary([s[metric] for s in samples])
            for metric in ("wallMs", "cpuMs", "selectQueries", "mappedSessionRows")}
            for size, samples in histories.items()}
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    print(json.dumps(summarize(args.directory), indent=2))
