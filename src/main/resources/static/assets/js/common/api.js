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
        const headerContainer = document.getElementById('app-header');
        const footerContainer = document.getElementById('app-footer');

        if (headerContainer) {
            const res = await fetch(this.getBaseUrl() + '/components/header.html');
            if (res.ok) {
                headerContainer.innerHTML = await res.text();
                const themeToggle = headerContainer.querySelector('#theme-toggle');
                if (themeToggle) SavoraTheme.bind(themeToggle);
                await this.syncAuthState();
            }
        }

        if (footerContainer) {
            const res = await fetch(this.getBaseUrl() + '/components/footer.html');
            if (res.ok) {
                footerContainer.innerHTML = await res.text();
            }
        }
    },

    async syncAuthState() {
        try {
            const res = await this.fetchApi('/api/auth/me');
            const user = res.data;
            if (!user) return;

            const authArea = document.getElementById('header-auth-area');
            if (authArea) {
                authArea.innerHTML = `
                    <span style="font-size: 0.9rem; font-weight: 600;">Welcome, ${user.fullName} (${user.roleName})</span>
                    <button class="btn btn-outline" id="btn-logout" style="padding: 7px 14px;">Sign Out</button>
                `;
                document.getElementById('btn-logout').addEventListener('click', async () => {
                    await this.fetchApi('/api/auth/logout', { method: 'POST' });
                    window.location.href = '/login';
                });
            }
        } catch (_) {
            // User is not signed in
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    SavoraApp.loadLayout();
});
