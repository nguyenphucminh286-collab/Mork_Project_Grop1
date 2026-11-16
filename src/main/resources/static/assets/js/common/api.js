/**
 * SAVORA FRONTEND CORE UTILITY (assets/js/common/api.js)
 * Shared utility across all HTML pages to call Spring Boot REST APIs and load Header/Footer dynamically
 */
const SavoraApp = {
    getBaseUrl() {
        if (window.location.port === '5500' || window.location.port === '5501') {
            return 'http://localhost:8083';
        }
        return '';
    },

    async fetchApi(endpoint, options = {}) {
        const url = this.getBaseUrl() + endpoint;
        const defaultHeaders = {
            'Content-Type': 'application/json'
        };
        const config = {
            credentials: 'include',
            ...options,
            headers: {
                ...defaultHeaders,
                ...(options.headers || {})
            }
        };

        const response = await fetch(url, config);
        const result = await response.json();
        if (!response.ok || result.success === false) {
            throw new Error(result.message || 'An error occurred while connecting to the server.');
        }
        return result;
    },

    formatCurrency(amount) {
        if (amount == null) return '0 VND';
        return Number(amount).toLocaleString('en-US') + ' VND';
    },

    async loadLayout() {
        const toggle = document.getElementById('theme-toggle');
        if (toggle && window.SavoraTheme) SavoraTheme.bind(toggle);
    },
    async syncAuthState() {
        // Thymeleaf renders the current session into the header fragment.
    }

};

document.addEventListener('DOMContentLoaded', () => {
    SavoraApp.loadLayout();
});
