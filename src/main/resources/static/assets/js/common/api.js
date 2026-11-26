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

    scrollToHomeHash(hash, smooth = true) {
        if (!hash) return false;
        const target = document.querySelector(hash);
        if (!target) return false;
        const header = document.querySelector('.header');
        const headerHeight = header ? header.getBoundingClientRect().height : 80;
        const top = Math.max(0, target.getBoundingClientRect().top + window.scrollY - headerHeight - 14);
        window.scrollTo({ top, behavior: smooth ? 'smooth' : 'auto' });
        return true;
    },

    initHeaderNavigation() {
        const header = document.querySelector('.header');
        if (header) {
            const syncHeaderShadow = () => {
                header.classList.toggle('is-scrolled', (window.scrollY || document.documentElement.scrollTop) > 12);
            };
            window.addEventListener('scroll', syncHeaderShadow, { passive: true });
            syncHeaderShadow();
        }

        const navLinks = [...document.querySelectorAll('.header__nav a')];
        if (!navLinks.length) return;

        const path = window.location.pathname;
        const isHomePage = path === '/' || path === '/home';

        const setActiveLink = (matchFn) => {
            navLinks.forEach((a) => a.classList.toggle('active', matchFn(a)));
        };

        if (!isHomePage) {
            setActiveLink((a) => {
                const url = new URL(a.href, window.location.origin);
                return url.pathname === path && !url.hash;
            });
            return;
        }

        const sectionHashes = ['#deals', '#tables-section', '#menu'];
        let lockScrollSpyUntil = 0;

        const syncHomeNavActive = () => {
            if (Date.now() < lockScrollSpyUntil) return;
            const headerHeight = header ? header.getBoundingClientRect().height : 80;
            const scrollY = window.scrollY || document.documentElement.scrollTop;
            const probeY = scrollY + headerHeight + 48;
            let activeHash = '';

            for (const hash of sectionHashes) {
                const el = document.querySelector(hash);
                if (el) {
                    const elTop = el.getBoundingClientRect().top + scrollY;
                    if (elTop <= probeY) {
                        activeHash = hash;
                    }
                }
            }

            setActiveLink((a) => {
                const url = new URL(a.href, window.location.origin);
                if (activeHash) {
                    return url.pathname === '/home' && url.hash === activeHash;
                }
                return url.pathname === '/home' && !url.hash;
            });
        };

        navLinks.forEach((link) => {
            link.addEventListener('click', (e) => {
                const url = new URL(link.href, window.location.origin);
                if (url.pathname !== '/home') return;

                e.preventDefault();
                lockScrollSpyUntil = Date.now() + 750;
                if (url.hash) {
                    history.replaceState(null, '', `/home${url.hash}`);
                    this.scrollToHomeHash(url.hash, true);
                    setActiveLink((a) => a === link);
                } else {
                    history.replaceState(null, '', '/home');
                    window.scrollTo({ top: 0, behavior: 'smooth' });
                    setActiveLink((a) => a === link);
                }
            });
        });

        window.addEventListener('scroll', syncHomeNavActive, { passive: true });
        syncHomeNavActive();
    },

    loadLayout() {
        const toggle = document.getElementById('theme-toggle');
        if (toggle && window.SavoraTheme) SavoraTheme.bind(toggle);
        this.initHeaderNavigation();
    }
};

document.addEventListener('DOMContentLoaded', () => SavoraApp.loadLayout());
