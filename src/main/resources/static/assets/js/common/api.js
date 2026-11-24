const SavoraApp = {
    getBaseUrl: () => ['5500', '5501'].includes(window.location.port) ? 'http://localhost:8083' : '',

    async fetchApi(endpoint, options = {}) {
        const res = await fetch(this.getBaseUrl() + endpoint, {
            credentials: 'include',
            ...options,
            headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
        });
        const result = await res.json();
        if (!res.ok || result.success === false) {
            throw new Error(result.message || 'Server error.');
        }
        return result;
    },

    formatCurrency: (amount) => amount == null ? '0 VND' : `${Number(amount).toLocaleString('en-US')} VND`,

    loadLayout() {
        const toggle = document.getElementById('theme-toggle');
        if (toggle && window.SavoraTheme) SavoraTheme.bind(toggle);
    }
};

document.addEventListener('DOMContentLoaded', () => SavoraApp.loadLayout());
