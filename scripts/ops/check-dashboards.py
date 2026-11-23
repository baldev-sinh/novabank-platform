#!/usr/bin/env python3
"""Checks the provisioned Grafana dashboards (7.7): valid JSON, unique uids and panel ids, and
every panel and query on the provisioned Prometheus data source (uid "prometheus"), so a
dashboard exported from someone's Grafana with their own data source id can't slip in.

Usage: python3 scripts/ops/check-dashboards.py [ops/grafana/dashboards]
"""
import json
import pathlib
import sys

DATASOURCE_UID = "prometheus"


def main(directory: str) -> int:
    problems = []
    uids = {}
    files = sorted(pathlib.Path(directory).glob("*.json"))
    if not files:
        problems.append(f"no dashboards in {directory}")
    for path in files:
        try:
            dashboard = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as ex:
            problems.append(f"{path.name}: not valid JSON ({ex})")
            continue
        uid = dashboard.get("uid")
        if not uid:
            problems.append(f"{path.name}: no uid (links and the home dashboard need a stable one)")
        elif uid in uids:
            problems.append(f"{path.name}: uid {uid} also used by {uids[uid]}")
        else:
            uids[uid] = path.name
        panel_ids = set()
        for panel in dashboard.get("panels", []):
            title = panel.get("title", "?")
            if panel.get("id") in panel_ids:
                problems.append(f"{path.name}: duplicate panel id {panel.get('id')} ({title})")
            panel_ids.add(panel.get("id"))
            sources = [panel.get("datasource")] + [t.get("datasource") for t in panel.get("targets", [])]
            for source in sources:
                if not isinstance(source, dict) or source.get("uid") != DATASOURCE_UID:
                    problems.append(f"{path.name}: panel '{title}' uses data source {source!r}")
            for target in panel.get("targets", []):
                if not target.get("expr", "").strip():
                    problems.append(f"{path.name}: panel '{title}' has an empty query")
    for problem in problems:
        print("FAIL:", problem)
    if not problems:
        print(f"ok: {len(files)} dashboards")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else "ops/grafana/dashboards"))
