#!/usr/bin/env python3

import argparse
import json
import os

from zt_security import ActionContext, ZtSecurityClient


def client() -> ZtSecurityClient:
    return ZtSecurityClient(
        os.environ["ZT_BASE_URL"],
        os.environ["ZT_API_KEY"],
        os.environ["ZT_TENANT_ID"],
        os.environ.get("ZT_WORKSPACE_ID"),
    )


def main() -> None:
    parser = argparse.ArgumentParser(prog="ztctl")
    subparsers = parser.add_subparsers(dest="command", required=True)

    lint = subparsers.add_parser("policy-lint")
    lint.add_argument("policy")

    evaluate = subparsers.add_parser("evaluate")
    evaluate.add_argument("--subject", required=True)
    evaluate.add_argument("--action", required=True)
    evaluate.add_argument("--resource", required=True)

    args = parser.parse_args()
    api = client()

    if args.command == "policy-lint":
        result = api.policy_lint(args.policy)
    else:
        result = api.governance.evaluate(
            ActionContext(
                subject=args.subject,
                action=args.action,
                resource=args.resource,
                tenant_id=os.environ["ZT_TENANT_ID"],
            )
        )

    print(json.dumps(result, indent=2, default=str))


if __name__ == "__main__":
    main()
