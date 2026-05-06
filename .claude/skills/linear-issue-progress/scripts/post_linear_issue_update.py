#!/usr/bin/env python3
"""Post a Linear issue comment with local images uploaded via fileUpload."""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.request


GRAPHQL_URL = "https://api.linear.app/graphql"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Upload local images through Linear fileUpload and post an issue comment."
    )
    parser.add_argument("--issue", required=True, help="Linear issue key, UUID, or URL.")
    parser.add_argument("--body-file", required=True, help="Markdown file for the comment body.")
    parser.add_argument(
        "--image",
        action="append",
        default=[],
        metavar="LABEL=PATH",
        help="Local image to upload. Repeat as needed.",
    )
    parser.add_argument(
        "--api-key-env",
        default="LINEAR_API_KEY",
        help="Environment variable holding the Linear API key.",
    )
    parser.add_argument(
        "--public",
        action="store_true",
        help="Request public asset URLs. Default is private Linear upload URLs.",
    )
    return parser.parse_args()


def graphql(api_key: str, query: str, variables: dict) -> dict:
    payload = json.dumps({"query": query, "variables": variables}).encode("utf-8")
    request = urllib.request.Request(
        GRAPHQL_URL,
        data=payload,
        headers={
            "Authorization": api_key,
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            data = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"Linear GraphQL HTTP {error.code}: {body}") from error
    if data.get("errors"):
        raise SystemExit("Linear GraphQL errors: " + json.dumps(data["errors"], indent=2))
    return data["data"]


def resolve_issue(api_key: str, issue_ref: str) -> dict:
    issue_key = re.search(r"\b[A-Z][A-Z0-9]+-\d+\b", issue_ref)
    issue_id = issue_key.group(0) if issue_key else issue_ref.rstrip("/").split("/")[-1]
    query = """
    query Issue($id: String!) {
      issue(id: $id) {
        id
        identifier
        title
        url
      }
    }
    """
    issue = graphql(api_key, query, {"id": issue_id})["issue"]
    if not issue:
        raise SystemExit(f"Linear issue not found: {issue_ref}")
    return issue


def parse_image_arg(value: str) -> tuple[str, Path]:
    if "=" not in value:
        raise SystemExit(f"--image must be LABEL=PATH, got: {value}")
    label, path = value.split("=", 1)
    label = label.strip()
    image_path = Path(path).expanduser()
    if not label:
        raise SystemExit(f"--image label is empty: {value}")
    if not image_path.is_file():
        raise SystemExit(f"--image file does not exist: {image_path}")
    return label, image_path


def upload_image(api_key: str, label: str, path: Path, make_public: bool) -> dict:
    content_type = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
    size = path.stat().st_size
    mutation = """
    mutation FileUpload($contentType: String!, $filename: String!, $size: Int!, $makePublic: Boolean) {
      fileUpload(contentType: $contentType, filename: $filename, size: $size, makePublic: $makePublic) {
        success
        uploadFile {
          uploadUrl
          assetUrl
          headers {
            key
            value
          }
        }
      }
    }
    """
    result = graphql(
        api_key,
        mutation,
        {
            "contentType": content_type,
            "filename": path.name,
            "size": size,
            "makePublic": make_public,
        },
    )["fileUpload"]
    if not result["success"]:
        raise SystemExit(f"Linear fileUpload failed for {path}")

    upload_file = result["uploadFile"]
    headers = {item["key"]: item["value"] for item in upload_file.get("headers", [])}
    headers["Content-Type"] = content_type
    request = urllib.request.Request(
        upload_file["uploadUrl"],
        data=path.read_bytes(),
        headers=headers,
        method="PUT",
    )
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            if response.status not in (200, 201, 204):
                raise SystemExit(f"Upload failed for {path}: HTTP {response.status}")
    except urllib.error.HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise SystemExit(f"Upload HTTP {error.code} for {path}: {body}") from error

    return {
        "label": label,
        "path": str(path),
        "contentType": content_type,
        "size": size,
        "assetUrl": upload_file["assetUrl"],
    }


def create_comment(api_key: str, issue_id: str, body: str) -> dict:
    mutation = """
    mutation CommentCreate($input: CommentCreateInput!) {
      commentCreate(input: $input) {
        success
        comment {
          id
          url
        }
      }
    }
    """
    result = graphql(api_key, mutation, {"input": {"issueId": issue_id, "body": body}})[
        "commentCreate"
    ]
    if not result["success"]:
        raise SystemExit("Linear commentCreate failed")
    return result["comment"]


def main() -> int:
    args = parse_args()
    api_key = os.environ.get(args.api_key_env)
    if not api_key:
        raise SystemExit(f"Missing Linear API key in ${args.api_key_env}")

    body_path = Path(args.body_file).expanduser()
    if not body_path.is_file():
        raise SystemExit(f"--body-file does not exist: {body_path}")
    body = body_path.read_text(encoding="utf-8").rstrip()

    issue = resolve_issue(api_key, args.issue)
    uploads = [
        upload_image(api_key, label, path, args.public)
        for label, path in (parse_image_arg(image) for image in args.image)
    ]
    if uploads:
        lines = ["", "", "Eval screenshots:"]
        for upload in uploads:
            label = upload["label"]
            lines.append(f"- {label}")
            lines.append(f"![{label}]({upload['assetUrl']})")
        body += "\n".join(lines)

    comment = create_comment(api_key, issue["id"], body)
    print(json.dumps({"issue": issue, "comment": comment, "uploads": uploads}, indent=2))
    return 0


if __name__ == "__main__":
    sys.exit(main())
