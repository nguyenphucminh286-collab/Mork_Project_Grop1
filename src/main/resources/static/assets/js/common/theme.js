const SavoraTheme = (() => {
    const root = document.documentElement;
    const storageKey = 'savora-theme';

    const applyTheme = (theme) => {
        const normalized = theme === 'dark' ? 'dark' : 'light';
        root.dataset.theme = normalized;
        document.querySelectorAll('#theme-toggle, .theme-toggle').forEach((btn) => {
            btn.setAttribute('aria-pressed', String(normalized === 'dark'));
            btn.setAttribute('title', normalized === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode');
        });
    };

    try {
        root.dataset.theme = localStorage.getItem(storageKey) === 'dark' ? 'dark' : 'light';
    } catch {
        root.dataset.theme = 'light';
    }

    const toggle = () => {
        const nextTheme = root.dataset.theme === 'dark' ? 'light' : 'dark';
        applyTheme(nextTheme);
        try {
            localStorage.setItem(storageKey, nextTheme);
        } catch {
            // Keep theme applied in memory if storage is blocked
        }
    };

    const bind = (button) => {
        if (!button) return;
        button.setAttribute('aria-pressed', String(root.dataset.theme === 'dark'));
        button.setAttribute('title', root.dataset.theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode');
        if (button.dataset.themeBound === 'true') return;
        button.dataset.themeBound = 'true';
        button.addEventListener('click', (e) => {
            e.preventDefault();
            toggle();
        });
    };

    const initAll = () => {
        document.querySelectorAll('#theme-toggle, .theme-toggle').forEach(bind);
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initAll);
    } else {
        initAll();
    }

    return {
        bind,
        toggle,
        applyTheme
    };
})();

window.SavoraTheme = SavoraTheme;
