(function () {
    const MAX_STORED_MESSAGES = 24;
    const MAX_REQUEST_HISTORY = 12;

    function readStorage(key) {
        if (!key) {
            return [];
        }
        try {
            const raw = window.sessionStorage.getItem(key);
            if (!raw) {
                return [];
            }
            const parsed = JSON.parse(raw);
            return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
            return [];
        }
    }

    function writeStorage(key, value) {
        if (!key) {
            return;
        }
        try {
            window.sessionStorage.setItem(key, JSON.stringify(value));
        } catch (error) {
            // Ignore storage failures.
        }
    }

    function clearStorage(key) {
        if (!key) {
            return;
        }
        try {
            window.sessionStorage.removeItem(key);
        } catch (error) {
            // Ignore storage failures.
        }
    }

    function normalizeRole(role) {
        return role === "user" ? "user" : "assistant";
    }

    function createMessageElement(message, isTyping) {
        const wrapper = document.createElement("article");
        wrapper.className = "student-ai-message" + (normalizeRole(message.role) === "user" ? " is-user" : " is-assistant");

        const bubble = document.createElement("div");
        bubble.className = "student-ai-bubble" + (isTyping ? " is-typing" : "");

        const label = document.createElement("div");
        label.className = "student-ai-message-role";
        label.textContent = normalizeRole(message.role) === "user" ? "You" : "PMS AI";

        const text = document.createElement("div");
        text.className = "student-ai-message-text";
        text.textContent = isTyping ? "Thinking..." : (message.content || "");

        bubble.appendChild(label);
        bubble.appendChild(text);
        wrapper.appendChild(bubble);
        return wrapper;
    }

    function initAiShell(shell) {
        const toggleButton = shell.querySelector(".js-student-ai-toggle");
        const panel = shell.querySelector(".student-ai-panel");
        const closeButton = shell.querySelector(".js-student-ai-close");
        const clearButton = shell.querySelector(".js-student-ai-clear");
        const feed = shell.querySelector(".js-student-ai-feed");
        const form = shell.querySelector(".js-student-ai-form");
        const input = shell.querySelector(".js-student-ai-input");
        const feedbackNode = shell.querySelector(".js-student-ai-feedback");

        if (!toggleButton || !panel || !feed || !form || !input) {
            return;
        }

        const endpoint = shell.dataset.endpoint || "";
        const storageKey = shell.dataset.storageKey || "";
        const welcomeMessage = shell.dataset.welcomeMessage || "Hello. I am PMS AI.";

        let isOpen = shell.dataset.open === "true";
        let isSending = false;
        let history = sanitizeStoredMessages(readStorage(storageKey));

        function applyOpenState(nextOpen) {
            isOpen = nextOpen;
            shell.dataset.open = nextOpen ? "true" : "false";
            shell.classList.toggle("is-open", nextOpen);
            toggleButton.setAttribute("aria-expanded", nextOpen ? "true" : "false");
            if (nextOpen) {
                window.requestAnimationFrame(function () {
                    input.focus();
                    scrollToBottom();
                });
            }
        }

        function sanitizeStoredMessages(messages) {
            if (!Array.isArray(messages)) {
                return [];
            }
            return messages
                .filter((item) => item && typeof item.content === "string")
                .map((item) => ({
                    role: normalizeRole(item.role),
                    content: item.content.trim()
                }))
                .filter((item) => item.content.length > 0)
                .slice(-MAX_STORED_MESSAGES);
        }

        function saveHistory() {
            writeStorage(storageKey, history.slice(-MAX_STORED_MESSAGES));
        }

        function showFeedback(text, isError) {
            if (!feedbackNode) {
                return;
            }
            if (!text) {
                feedbackNode.hidden = true;
                feedbackNode.textContent = "";
                feedbackNode.classList.remove("is-error");
                return;
            }
            feedbackNode.hidden = false;
            feedbackNode.textContent = text;
            feedbackNode.classList.toggle("is-error", Boolean(isError));
        }

        function syncSendState() {
            input.readOnly = isSending;
        }

        function scrollToBottom() {
            feed.scrollTop = feed.scrollHeight;
        }

        function renderMessages() {
            feed.innerHTML = "";
            const renderList = history.length > 0
                ? history
                : [{ role: "assistant", content: welcomeMessage }];

            renderList.forEach(function (message) {
                feed.appendChild(createMessageElement(message, false));
            });

            scrollToBottom();
        }

        function appendTypingIndicator() {
            const node = createMessageElement({ role: "assistant", content: "" }, true);
            node.classList.add("js-student-ai-typing");
            feed.appendChild(node);
            scrollToBottom();
        }

        function removeTypingIndicator() {
            const node = feed.querySelector(".js-student-ai-typing");
            if (node) {
                node.remove();
            }
        }

        function buildRequestHistory() {
            return history.slice(-MAX_REQUEST_HISTORY).map(function (item) {
                return {
                    role: normalizeRole(item.role),
                    content: item.content
                };
            });
        }

        async function sendMessage() {
            const message = input.value.trim();
            if (!message || isSending) {
                syncSendState();
                return;
            }

            const requestHistory = buildRequestHistory();
            history.push({ role: "user", content: message });
            history = history.slice(-MAX_STORED_MESSAGES);
            saveHistory();
            renderMessages();
            showFeedback("", false);
            input.value = "";
            isSending = true;
            syncSendState();
            appendTypingIndicator();

            try {
                const response = await fetch(endpoint, {
                    method: "POST",
                    headers: {
                        "Content-Type": "application/json",
                        "X-Requested-With": "XMLHttpRequest"
                    },
                    body: JSON.stringify({
                        message: message,
                        history: requestHistory
                    })
                });

                const payload = await response.json().catch(() => ({ message: "Unable to reach the AI assistant." }));
                if (!response.ok) {
                    throw new Error(payload.message || "Unable to reach the AI assistant.");
                }

                const reply = typeof payload.reply === "string" ? payload.reply.trim() : "";
                if (!reply) {
                    throw new Error("AI assistant returned an empty response.");
                }

                history.push({ role: "assistant", content: reply });
                history = history.slice(-MAX_STORED_MESSAGES);
                saveHistory();
                removeTypingIndicator();
                renderMessages();
            } catch (error) {
                removeTypingIndicator();
                showFeedback(error.message || "Unable to reach the AI assistant.", true);
            } finally {
                isSending = false;
                syncSendState();
                input.focus();
            }
        }

        toggleButton.addEventListener("click", function () {
            applyOpenState(!isOpen);
        });

        closeButton.addEventListener("click", function () {
            applyOpenState(false);
        });

        clearButton.addEventListener("click", function () {
            history = [];
            clearStorage(storageKey);
            showFeedback("", false);
            renderMessages();
            input.focus();
        });

        form.addEventListener("submit", function (event) {
            event.preventDefault();
            sendMessage();
        });

        input.addEventListener("input", syncSendState);
        input.addEventListener("keydown", function (event) {
            if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });

        document.addEventListener("keydown", function (event) {
            if (event.key === "Escape" && isOpen) {
                applyOpenState(false);
            }
        });

        applyOpenState(isOpen);
        renderMessages();
        syncSendState();
    }

    document.addEventListener("DOMContentLoaded", function () {
        document.querySelectorAll(".js-student-ai-shell").forEach(initAiShell);
    });
})();
