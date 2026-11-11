const SavoraTheme = (() => {
    const root = document.documentElement;
    const storageKey = 'savora-theme';

    try {
        root.dataset.theme = localStorage.getItem(storageKey) === 'dark' ? 'dark' : 'light';
    } catch {
        root.dataset.theme = 'light';
    }

    return {
        bind(button) {
            const updateButton = () => {
                button.setAttribute('aria-pressed', String(root.dataset.theme === 'dark'));
            };

            updateButton();
            button.addEventListener('click', () => {
                const theme = root.dataset.theme === 'dark' ? 'light' : 'dark';
                root.dataset.theme = theme;
                updateButton();

                try {
                    localStorage.setItem(storageKey, theme);
                } catch {
                    // The current page can still switch themes when storage is unavailable.
                }
            });
        }
    };
})();
