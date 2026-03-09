(function () {
    const key = 'pms-backoffice-sidebar-collapsed';
    function apply() {
        const collapsed = localStorage.getItem(key) === '1';
        document.body.classList.toggle('backoffice-sidebar-collapsed', collapsed && window.innerWidth > 900);
    }
    window.toggleBackofficeSidebar = function () {
        const next = localStorage.getItem(key) === '1' ? '0' : '1';
        localStorage.setItem(key, next);
        apply();
    };
    window.addEventListener('resize', apply);
    document.addEventListener('DOMContentLoaded', apply);
})();
