(function () {
    const POLL_INTERVAL_MS = 15000;
    let countRequestInFlight = false;
    let feedRequestInFlight = false;

    function updateNotificationCount(count) {
        const safeCount = Number.isFinite(count) ? Math.max(0, count) : 0;

        document.querySelectorAll(".js-student-notification-count").forEach((node) => {
            if (safeCount > 0) {
                node.textContent = `(${safeCount})`;
                node.style.display = "";
            } else {
                node.textContent = "(0)";
                node.style.display = "none";
            }
        });

        document.querySelectorAll(".js-student-notification-summary").forEach((node) => {
            node.textContent = String(safeCount);
        });
    }

    function resolveCountUrl() {
        const link = document.querySelector(".js-student-notification-link[data-notification-count-url]");
        return link ? link.dataset.notificationCountUrl : null;
    }

    async function refreshNotificationCount() {
        const countUrl = resolveCountUrl();
        if (!countUrl || countRequestInFlight) {
            return;
        }

        countRequestInFlight = true;
        try {
            const response = await fetch(countUrl, {
                headers: {
                    "X-Requested-With": "XMLHttpRequest",
                    "Accept": "application/json"
                },
                cache: "no-store"
            });
            if (!response.ok || response.redirected) {
                return;
            }

            const payload = await response.json();
            updateNotificationCount(payload && typeof payload.count === "number" ? payload.count : 0);
        } catch (error) {
            // Silent fail: polling should not disrupt the current page.
        } finally {
            countRequestInFlight = false;
        }
    }

    async function refreshNotificationsFeed() {
        const feed = document.getElementById("student-notifications-feed");
        if (!feed || feedRequestInFlight) {
            return;
        }

        const feedUrl = feed.dataset.feedUrl;
        if (!feedUrl) {
            return;
        }

        feedRequestInFlight = true;
        try {
            const url = new URL(feedUrl, window.location.origin);
            url.searchParams.set("page", feed.dataset.page || "1");

            const response = await fetch(url.toString(), {
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                },
                cache: "no-store"
            });
            if (!response.ok || response.redirected) {
                return;
            }

            const html = (await response.text()).trim();
            if (!html) {
                return;
            }

            const wrapper = document.createElement("div");
            wrapper.innerHTML = html;
            const nextFeed = wrapper.firstElementChild;
            if (!nextFeed) {
                return;
            }

            feed.replaceWith(nextFeed);
        } catch (error) {
            // Silent fail: polling should not disrupt the current page.
        } finally {
            feedRequestInFlight = false;
        }
    }

    async function runRefreshCycle() {
        if (document.hidden) {
            return;
        }

        await refreshNotificationsFeed();
        await refreshNotificationCount();
    }

    document.addEventListener("DOMContentLoaded", function () {
        runRefreshCycle();
        window.setInterval(runRefreshCycle, POLL_INTERVAL_MS);
    });

    document.addEventListener("visibilitychange", function () {
        if (!document.hidden) {
            runRefreshCycle();
        }
    });
})();
