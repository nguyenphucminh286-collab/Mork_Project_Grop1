const SavoraRealtimeChannel = (() => {
    try {
        return typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('savora-realtime-sync') : null;
    } catch {
        return null;
    }
})();

const SavoraApp = {
    getBaseUrl: () => ['5500', '5501'].includes(window.location.port) ? 'http://localhost:8083' : '',

    async fetchApi(endpoint, options = {}) {
        const method = (options.method || 'GET').toUpperCase();
        const res = await fetch(this.getBaseUrl() + endpoint, {
            credentials: 'include',
            ...options,
            headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
        });
        const rawText = await res.text();
        let result = {};
        try {
            result = rawText ? JSON.parse(rawText) : {};
        } catch {
            result = {};
        }
        if (!res.ok || result.success === false) {
            throw new Error(result.message || result.error || `Server error (HTTP ${res.status}).`);
        }
        if (method !== 'GET') {
            this.notifyRealtimeSync(endpoint);
        }
        return result;
    },

    notifyRealtimeSync(source = '') {
        const payload = { source, ts: Date.now() };
        try {
            if (SavoraRealtimeChannel) {
                SavoraRealtimeChannel.postMessage(payload);
            }
        } catch (_) {}
        try {
            localStorage.setItem('savora-realtime-pulse', JSON.stringify(payload));
        } catch (_) {}
    },

    onRealtimeSync(callback) {
        if (typeof callback !== 'function') return;
        try {
            if (SavoraRealtimeChannel) {
                SavoraRealtimeChannel.addEventListener('message', () => callback());
            }
        } catch (_) {}
        window.addEventListener('storage', (e) => {
            if (e.key === 'savora-realtime-pulse') {
                callback();
            }
        });
        window.addEventListener('focus', () => callback());
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') {
                callback();
            }
        });
    },

    formatCurrency: (amount) => amount == null ? '0 VND' : `${Number(amount).toLocaleString('en-US')} VND`,

    loadLayout() {
        const toggle = document.getElementById('theme-toggle');
        if (toggle && window.SavoraTheme) SavoraTheme.bind(toggle);
    }
};

document.addEventListener('DOMContentLoaded', () => SavoraApp.loadLayout());
