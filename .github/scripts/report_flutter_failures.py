"""Turns the failures in a `flutter test --file-reporter json:<file>` report into GitHub annotations.

The job log is only readable when signed in to GitHub; annotations are public and name each failed test
with its error, which is what is needed to fix it.
"""

import json
import sys

# GitHub shows at most 10 error annotations per step.
MAX_ANNOTATIONS = 10
MAX_MESSAGE = 3000


def escape(text: str) -> str:
    """Escapes what the workflow command syntax treats as special."""
    return text.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")


def main(report_path: str) -> None:
    names: dict[int, str] = {}
    errors: dict[int, list[str]] = {}
    failed: list[int] = []

    with open(report_path, encoding="utf-8") as report:
        for line in report:
            try:
                event = json.loads(line)
            except json.JSONDecodeError:
                continue
            if not isinstance(event, dict):
                continue
            kind = event.get("type")
            if kind == "testStart":
                names[event["test"]["id"]] = event["test"]["name"]
            elif kind == "error":
                errors.setdefault(event["testID"], []).append(
                    f"{event.get('error', '')}\n{event.get('stackTrace', '')}"
                )
            elif kind == "testDone" and event.get("result") != "success" and not event.get("hidden"):
                failed.append(event["testID"])

    for test_id in failed[:MAX_ANNOTATIONS]:
        title = escape(names.get(test_id, f"test {test_id}")).replace(",", "%2C").replace(":", "%3A")
        message = "\n".join(errors.get(test_id, ["(no error recorded)"]))[:MAX_MESSAGE]
        print(f"::error title={title}::{escape(message)}")
    if len(failed) > MAX_ANNOTATIONS:
        print(f"::warning::{len(failed) - MAX_ANNOTATIONS} more failed tests are not shown")


if __name__ == "__main__":
    main(sys.argv[1])
