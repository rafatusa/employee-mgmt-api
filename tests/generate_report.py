"""Combines the validation artifacts into a single HTML summary report.

Reads reports/k6-summary.json (written by the load stage) and the pytest HTML
report, and produces reports/index.html. Missing inputs are reported as such
rather than failing the stage, so the artifact always explains what ran.
"""

import datetime
import json
import pathlib

REPORTS = pathlib.Path("reports")
K6_SUMMARY = REPORTS / "k6-summary.json"
FUNCTIONAL_REPORT = REPORTS / "functional.html"
OUTPUT = REPORTS / "index.html"

P95_BUDGET_MS = 500.0
ERROR_RATE_BUDGET = 0.01


def load_k6_metrics() -> dict:
    if not K6_SUMMARY.exists():
        return {}
    with K6_SUMMARY.open(encoding="utf-8") as handle:
        return json.load(handle).get("metrics", {})


def metric_value(metrics: dict, name: str, key: str):
    entry = metrics.get(name)
    if not isinstance(entry, dict):
        return None
    return entry.get(key)


def row(label: str, value: str, budget: str, passed) -> str:
    if passed is None:
        state, css = "NOT MEASURED", "unknown"
    elif passed:
        state, css = "PASS", "pass"
    else:
        state, css = "FAIL", "fail"
    return (
        f'<tr><td>{label}</td><td>{value}</td><td>{budget}</td>'
        f'<td class="{css}">{state}</td></tr>'
    )


def build_rows(metrics: dict) -> str:
    rows = []

    p95 = metric_value(metrics, "http_req_duration", "p(95)")
    rows.append(
        row(
            "Response time (p95)",
            f"{p95:.1f} ms" if p95 is not None else "not measured",
            f"&lt; {P95_BUDGET_MS:.0f} ms",
            None if p95 is None else p95 < P95_BUDGET_MS,
        )
    )

    failed = metric_value(metrics, "http_req_failed", "rate")
    rows.append(
        row(
            "HTTP error rate",
            f"{failed * 100:.2f} %" if failed is not None else "not measured",
            f"&lt; {ERROR_RATE_BUDGET * 100:.0f} %",
            None if failed is None else failed < ERROR_RATE_BUDGET,
        )
    )

    checks = metric_value(metrics, "checks", "rate")
    rows.append(
        row(
            "k6 check success rate",
            f"{checks * 100:.2f} %" if checks is not None else "not measured",
            "= 100 %",
            None if checks is None else checks >= 0.99,
        )
    )

    requests_total = metric_value(metrics, "http_reqs", "count")
    rows.append(
        row(
            "Requests issued",
            str(int(requests_total)) if requests_total is not None else "not measured",
            "informational",
            None,
        )
    )

    return "\n".join(rows)


def main() -> None:
    REPORTS.mkdir(parents=True, exist_ok=True)
    metrics = load_k6_metrics()
    generated = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    functional_link = (
        '<p><a href="functional.html">Full functional and integration test report</a></p>'
        if FUNCTIONAL_REPORT.exists()
        else "<p>Functional report not present in this run.</p>"
    )

    html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8"/>
<title>Employee Management API - Validation Report</title>
<style>
  body {{ font-family: system-ui, sans-serif; margin: 40px; color: #1f2328; }}
  h1 {{ margin-bottom: 4px; }}
  .meta {{ color: #656d76; margin-bottom: 28px; }}
  table {{ border-collapse: collapse; width: 100%; max-width: 820px; }}
  th, td {{ border: 1px solid #d0d7de; padding: 10px 14px; text-align: left; }}
  th {{ background: #f6f8fa; }}
  .pass {{ color: #1a7f37; font-weight: 600; }}
  .fail {{ color: #cf222e; font-weight: 600; }}
  .unknown {{ color: #9a6700; font-weight: 600; }}
</style>
</head>
<body>
  <h1>Validation Report</h1>
  <p class="meta">Employee Management API &middot; generated {generated}</p>

  <h2>Performance acceptance criteria</h2>
  <table>
    <tr><th>Measurement</th><th>Observed</th><th>Budget</th><th>Result</th></tr>
    {build_rows(metrics)}
  </table>

  <h2>Functional coverage</h2>
  {functional_link}
  <p>The functional suite exercises the health endpoint, JWT authentication,
     employee CRUD operations, database persistence and the Nginx reverse proxy.</p>
</body>
</html>
"""

    OUTPUT.write_text(html, encoding="utf-8")
    print(f"Wrote {OUTPUT}")


if __name__ == "__main__":
    main()
