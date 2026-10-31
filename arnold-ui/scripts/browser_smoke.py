import json
import os
from urllib.parse import urlparse

from playwright.sync_api import sync_playwright


def merge_request(number, title, repository, roles, blocked=False, review_state="REVIEW_READY"):
    now = 1_721_900_000_000 + number
    return {
        "mergeRequest": {
            "id": f"gitlab:10:{number}",
            "provider": "GITLAB",
            "repositoryId": "10",
            "providerId": str(1000 + number),
            "number": number,
            "repositoryPath": repository,
            "title": title,
            "description": "A synchronized merge request",
            "webUrl": f"https://gitlab.example/{repository}/-/merge_requests/{number}",
            "sourceBranch": f"feature/{number}",
            "targetBranch": "main",
            "headSha": "abc123",
            "state": "opened",
            "mergeStatus": "cannot_be_merged" if blocked else "can_be_merged",
            "draft": False,
            "authorId": 10,
            "assigneeIds": [10],
            "reviewerIds": [10],
            "labels": ["backend"],
            "createdAt": now - 86_400_000,
            "mergedAt": 0,
            "closedAt": 0,
            "hasConflicts": blocked,
            "blockingDiscussionsResolved": not blocked,
            "providerUpdatedAt": now,
            "lastSyncedAt": now,
        },
        "roles": roles,
        "reviewState": review_state,
        "blocked": blocked,
    }


MR_ONE = merge_request(482, "feat: implement neural cache layer", "core-infrastructure", ["reviewer"], True)
MR_TWO = merge_request(479, "fix: prevent thread starvation in ledger", "ledger-service", ["author"],
                       review_state="CREATED")
MR_THREE = merge_request(475, "security: rotate API credentials", "platform-secrets", ["assignee"], False)

SYNC = {
    "provider": "GITLAB",
    "status": "SUCCEEDED",
    "lastAttemptAt": 1_721_900_000_000,
    "lastSucceededAt": 1_721_900_000_000,
    "nextDueAt": 1_721_900_900_000,
    "failure": None,
    "retryable": False,
}

STATS = {
    "scope": "personal",
    "openAuthored": 2,
    "openReviewWork": 3,
    "merged": 18,
    "medianTimeToMergeMs": 8_280_000,
    "throughputDeltaPct": 12.0,
    "weekly": [
        {"weekStart": "2026-06-08", "authoredMerged": 3, "reviewWorkCompleted": 6},
        {"weekStart": "2026-06-15", "authoredMerged": 4, "reviewWorkCompleted": 8},
        {"weekStart": "2026-06-22", "authoredMerged": 3, "reviewWorkCompleted": 7},
        {"weekStart": "2026-06-29", "authoredMerged": 5, "reviewWorkCompleted": 10},
        {"weekStart": "2026-07-06", "authoredMerged": 4, "reviewWorkCompleted": 10},
        {"weekStart": "2026-07-13", "authoredMerged": 6, "reviewWorkCompleted": 12},
    ],
    "generatedAt": 1_721_900_000_000,
    "dataThrough": 1_721_900_000_000,
}

QUEUE = {
    "items": [
        {
            "id": "queue-1",
            "targetType": "MERGE_REQUEST",
            "targetId": "gitlab:10:482",
            "role": "reviewer",
            "source": "SYNC",
            "blocked": True,
            "mergeRequest": MR_ONE,
            "group": None,
        },
        {
            "id": "queue-group",
            "targetType": "GROUP",
            "targetId": "group-auth",
            "role": "author",
            "source": "MANUAL",
            "blocked": False,
            "mergeRequest": None,
            "group": {
                "id": "group-auth",
                "name": "Auth V2 rollout",
                "members": [MR_TWO, MR_THREE],
                "createdAt": 1_721_800_000_000,
                "updatedAt": 1_721_900_000_000,
            },
        },
    ],
    "updatedAt": 1_721_900_000_000,
    "version": 3,
    "groupVersion": 1,
}

NOTIFICATIONS = {
    "items": [{
        "id": "notification-1",
        "type": "REVIEW_REQUESTED",
        "title": "Review requested",
        "body": "feat: implement neural cache layer",
        "aggregateType": "change_request",
        "aggregateId": "gitlab:10:482",
        "deepLink": "/review?provider=GITLAB&repositoryId=10&number=482",
        "read": False,
        "readAt": 0,
        "createdAt": 1_721_900_000_000,
    }],
    "unreadCount": 1,
    "nextCursor": None,
}

DASHBOARD = {
    "user": {
        "id": "user-1",
        "email": "tushar@example.com",
        "username": "Tushar Naik",
        "displayName": "Tushar",
        "avatarUrl": None,
        "providerIdentities": {
            "GITLAB": {
                "providerUserId": "10",
                "username": "tushar.naik",
                "avatarUrl": None,
            },
        },
        "roles": [],
    },
    "integration": {
        "connected": True,
        "provider": "GITLAB",
        "credentialType": "OAUTH",
        "providerUserId": "10",
        "providerUsername": "tushar.naik",
        "scopes": ["api"],
        "expiresAt": 4_102_444_800_000,
        "status": "connected",
    },
    "sync": SYNC,
    "queue": QUEUE,
    "openMergeRequests": [MR_TWO, MR_ONE],
    "productivity": STATS,
    "notifications": NOTIFICATIONS,
}

SYNC_CONTROL = {
    "configuredMode": "AUTO",
    "effectiveMode": "POLLING",
    "callbackObserved": True,
    "lastCallbackAt": 1_721_900_000_000,
    "pollingEnabled": True,
    "leaderId": None,
    "leaderLeaseUntil": 0,
    "currentNodeLeader": False,
    "currentNodeId": "arnold-node-a:instance-1",
}


def envelope(response):
    return {"success": True, "response": response}


def run():
    base_url = os.environ.get("ARNOLD_UI_URL", "http://127.0.0.1:3100")
    api_requests = []
    console_errors = []
    page_errors = []

    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(
            headless=True,
            executable_path="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        )
        page = browser.new_page(viewport={"width": 1440, "height": 1000}, device_scale_factor=1)
        page.add_init_script(
            "localStorage.setItem('arnold.splash.lastShownAt', String(Date.now()));"
            "localStorage.setItem('arnold.theme', 'light');"
        )
        page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
        page.on("pageerror", lambda error: page_errors.append(str(error)))

        def route_api(route):
            path = urlparse(route.request.url).path
            api_requests.append(path)
            if path == "/api/app/v1/bootstrap":
                response = DASHBOARD
            elif path == "/api/merge-requests/v1/mine":
                response = {
                    "items": [MR_ONE, MR_TWO, MR_THREE],
                    "nextCursor": None,
                    "syncs": {"GITLAB": SYNC},
                }
            elif path == "/api/merge-queues/v2/mine":
                response = QUEUE
            elif path == "/api/stats/v1/mine":
                response = STATS
            elif path == "/api/stats/v1/organization":
                response = {**STATS, "scope": "organization", "openAuthored": 42, "openReviewWork": 71}
            elif path == "/api/merge-request-groups/v1/mine":
                response = {"groups": [QUEUE["items"][1]["group"]], "version": 1, "updatedAt": 1_721_900_000_000}
            elif path == "/api/notifications/v1/mine" or path.startswith("/api/notifications/v1/mine/"):
                response = NOTIFICATIONS
            elif path == "/api/gitlab-syncs/v1/mine":
                response = {**SYNC, "status": "QUEUED"}
            elif path == "/api/admin/v1/user-sync":
                response = SYNC_CONTROL
            elif path == "/api/admin/v1/user-sync/callback-observation/reset":
                response = {
                    **SYNC_CONTROL,
                    "effectiveMode": "POLLING",
                    "callbackObserved": False,
                    "lastCallbackAt": 0,
                    "pollingEnabled": True,
                }
            elif path == "/api/merge-queues/v1/mine/reorder":
                response = {"items": [], "version": 4, "updatedAt": 1_721_900_000_000}
            else:
                route.fulfill(status=404, content_type="application/json", body=json.dumps({"message": path}))
                return
            route.fulfill(status=200, content_type="application/json", body=json.dumps(envelope(response)))

        page.route("**/api/**", route_api)

        page.goto(f"{base_url}/")
        page.wait_for_load_state("networkidle")
        page.get_by_role("heading", name="Welcome, Tushar.").wait_for()
        assert api_requests.count("/api/app/v1/bootstrap") == 1
        page.screenshot(path="/private/tmp/arnold-home.png", full_page=True)

        page.get_by_role("button", name="1 unread notifications").click()
        page.get_by_role("heading", name="Notifications").wait_for()
        page.screenshot(path="/private/tmp/arnold-notifications.png", full_page=True)

        page.goto(f"{base_url}/merge-requests")
        page.wait_for_load_state("networkidle")
        page.wait_for_url(f"{base_url}/queue")
        page.get_by_role("heading", name="Merge requests").wait_for()
        page.get_by_text("Auth V2 rollout").wait_for()
        page.get_by_role("button", name="Ready to merge").click()
        page.get_by_text("Auth V2 rollout").wait_for()
        page.get_by_role("button", name="All · 2").click()
        page.get_by_role("button", name="Group MRs").click()
        page.get_by_role("heading", name="Create a review group").wait_for()
        page.screenshot(path="/private/tmp/arnold-queue.png", full_page=True)

        page.goto(f"{base_url}/stats")
        page.wait_for_load_state("networkidle")
        page.get_by_role("heading", name="Organisation insights").wait_for()
        page.get_by_text("Organisation insights are coming soon").wait_for()
        page.screenshot(path="/private/tmp/arnold-stats.png", full_page=True)

        page.goto(f"{base_url}/admin")
        page.wait_for_load_state("networkidle")
        page.get_by_role("heading", name="Admin", exact=True).wait_for()
        page.get_by_text("Polling", exact=True).first.wait_for()
        page.screenshot(path="/private/tmp/arnold-admin.png", full_page=True)
        page.get_by_role("button", name="Reset detection").click()
        page.get_by_text("Waiting for callback", exact=True).wait_for()

        page.set_viewport_size({"width": 390, "height": 844})
        page.goto(f"{base_url}/merge-requests")
        page.wait_for_load_state("networkidle")
        page.wait_for_url(f"{base_url}/queue")
        page.get_by_role("heading", name="Merge requests").wait_for()
        page.screenshot(path="/private/tmp/arnold-open-mrs-mobile.png", full_page=True)

        page.goto(f"{base_url}/admin")
        page.wait_for_load_state("networkidle")
        page.get_by_role("heading", name="Admin", exact=True).wait_for()
        page.screenshot(path="/private/tmp/arnold-admin-mobile.png", full_page=True)

        browser.close()

    assert not page_errors, page_errors
    assert not console_errors, console_errors
    print(json.dumps({
        "apiRequests": api_requests,
        "screenshots": [
            "/private/tmp/arnold-home.png",
            "/private/tmp/arnold-notifications.png",
            "/private/tmp/arnold-queue.png",
            "/private/tmp/arnold-stats.png",
            "/private/tmp/arnold-admin.png",
            "/private/tmp/arnold-open-mrs-mobile.png",
            "/private/tmp/arnold-admin-mobile.png",
        ],
    }, indent=2))


if __name__ == "__main__":
    run()
