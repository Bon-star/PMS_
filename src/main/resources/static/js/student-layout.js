(function () {
    var storageKey = "student_sidebar_collapsed";

    function isCollapsed() {
        return document.body.classList.contains("sidebar-collapsed");
    }

    function updateToggleLabel(collapsed) {
        document.querySelectorAll(".sidebar-toggle .toggle-text").forEach(function (el) {
            el.textContent = collapsed ? "Mở rộng" : "Thu gọn";
        });
    }

    function applyState(collapsed) {
        document.body.classList.toggle("sidebar-collapsed", collapsed);
        updateToggleLabel(collapsed);
    }

    function loadState() {
        var stored = localStorage.getItem(storageKey);
        applyState(stored === "1");
    }

    window.toggleStudentSidebar = function () {
        var collapsed = !isCollapsed();
        applyState(collapsed);
        localStorage.setItem(storageKey, collapsed ? "1" : "0");
    };

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", loadState);
    } else {
        loadState();
    }
})();
