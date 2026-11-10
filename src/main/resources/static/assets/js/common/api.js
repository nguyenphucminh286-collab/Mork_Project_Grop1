/**
 * SAVORA FRONTEND CORE UTILITY (assets/js/common/api.js)
 * Dùng chung cho toàn bộ các trang HTML để gọi REST API Spring Boot và nạp Header/Footer tự động
 */
const SavoraApp = {
    // Tự động nhận diện base URL (hỗ trợ cả khi chạy trực tiếp cổng 8083 hoặc Live Server 5500 trên VS Code)
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
            throw new Error(result.message || 'Có lỗi xảy ra khi kết nối máy chủ');
        }
        return result;
    },

    formatCurrency(amount) {
        if (amount == null) return '0 đ';
        return Number(amount).toLocaleString('vi-VN') + ' đ';
    },

    async loadLayout() {
        const headerContainer = document.getElementById('app-header');
        const footerContainer = document.getElementById('app-footer');

        if (headerContainer) {
            const res = await fetch(this.getBaseUrl() + '/components/header.html');
            if (res.ok) {
                headerContainer.innerHTML = await res.text();
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
                    <span style="font-size: 0.9rem; font-weight: 600;">Xin chào, ${user.fullName} (${user.roleName})</span>
                    <button class="btn btn-outline" id="btn-logout" style="padding: 7px 14px;">Đăng xuất</button>
                `;
                document.getElementById('btn-logout').addEventListener('click', async () => {
                    await this.fetchApi('/api/auth/logout', { method: 'POST' });
                    window.location.href = '/login';
                });
            }
        } catch (_) {
            // Người dùng chưa đăng nhập
        }
    }
};

document.addEventListener('DOMContentLoaded', () => {
    SavoraApp.loadLayout();
});
