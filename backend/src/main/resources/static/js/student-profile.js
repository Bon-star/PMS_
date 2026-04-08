(function () {
    function bindPasswordToggles() {
        document.querySelectorAll("[data-password-toggle]").forEach(function (button) {
            if (button.dataset.bound === "true") {
                return;
            }

            button.dataset.bound = "true";
            button.addEventListener("click", function () {
                var targetId = button.getAttribute("data-target");
                if (!targetId) {
                    return;
                }

                var input = document.getElementById(targetId);
                if (!input) {
                    return;
                }

                var showing = input.type === "text";
                input.type = showing ? "password" : "text";
                button.textContent = showing ? "Show" : "Hide";
                button.setAttribute("aria-label", (showing ? "Show " : "Hide ") + input.name.replace(/([A-Z])/g, " $1").toLowerCase());
            });
        });
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", bindPasswordToggles);
    } else {
        bindPasswordToggles();
    }
})();
