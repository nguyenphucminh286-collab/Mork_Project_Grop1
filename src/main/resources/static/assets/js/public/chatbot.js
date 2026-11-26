/**
 * SAVORA — Gemini AI Assistant & Restaurant Concierge Chatbox
 * Supports multi-turn conversation, live Savora database context, token telemetry, and local fallback.
 */
document.addEventListener('DOMContentLoaded', () => {
    const launcher = document.getElementById('savora-chat-launcher');
    const win = document.getElementById('savora-chat-window');
    const closeBtn = document.getElementById('savora-chat-close');
    const clearBtn = document.getElementById('savora-chat-clear');
    const subtitleEl = document.getElementById('savora-chat-subtitle');
    const msgBox = document.getElementById('savora-chat-messages');
    const form = document.getElementById('savora-chat-form');
    const input = document.getElementById('savora-chat-input');
    const sendBtn = document.getElementById('savora-chat-send');
    const chipsBox = document.getElementById('savora-chat-chips');

    if (!launcher || !win || !msgBox) return;

    const HISTORY_KEY = 'savora_ai_chat_history_v2';
    const MAX_STORED_TURNS = 12;
    let conversationHistory = [];
    let isSending = false;

    const escapeHtml = (str) => String(str ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');

    const formatMarkdownText = (text) => {
        if (!text) return '';
        const codeBlocks = [];
        let processed = String(text).replace(/```(?:\w+)?\n?([\s\S]*?)```/g, (_, code) => {
            const idx = codeBlocks.length;
            codeBlocks.push(`<pre class="savora-chat-code-block"><code>${escapeHtml(code.trim())}</code></pre>`);
            return `__SAVORA_CODE_BLOCK_${idx}__`;
        });

        processed = escapeHtml(processed)
            .replace(/`([^`\n]+)`/g, '<code class="savora-chat-inline-code">$1</code>')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.+?)\*/g, '<em>$1</em>')
            .replace(/\n/g, '<br>');

        codeBlocks.forEach((blockHtml, idx) => {
            processed = processed.replace(`__SAVORA_CODE_BLOCK_${idx}__`, blockHtml);
        });
        return processed;
    };

    const loadStoredHistory = () => {
        try {
            const raw = sessionStorage.getItem(HISTORY_KEY);
            const parsed = raw ? JSON.parse(raw) : [];
            return Array.isArray(parsed) ? parsed.slice(-MAX_STORED_TURNS) : [];
        } catch {
            return [];
        }
    };

    const saveStoredHistory = () => {
        try {
            sessionStorage.setItem(HISTORY_KEY, JSON.stringify(conversationHistory.slice(-MAX_STORED_TURNS)));
        } catch {
        }
    };

    const appendMessage = (sender, text, links = [], blocked = false, meta = null) => {
        const div = document.createElement('div');
        div.className = `savora-chat-msg ${sender === 'user' ? 'savora-chat-msg--user' : 'savora-chat-msg--bot'} ${blocked ? 'savora-chat-msg--blocked' : ''}`;

        let html = `<div>${formatMarkdownText(text)}</div>`;
        if (sender === 'bot' && Array.isArray(links) && links.length > 0) {
            html += `<div class="savora-chat-nav-links">` +
                links.map((l) => `<a href="${escapeHtml(l.url)}" class="savora-chat-nav-link">${escapeHtml(l.label)} →</a>`).join('') +
                `</div>`;
        }
        if (sender === 'bot' && meta && meta.label) {
            html += `<div class="savora-chat-meta">${escapeHtml(meta.label)}</div>`;
        }
        div.innerHTML = html;

        div.querySelectorAll('.savora-chat-nav-link').forEach((a) => {
            a.addEventListener('click', (e) => {
                const href = a.getAttribute('href') || '';
                if (href.includes('#')) {
                    const [pathPart, hashPart] = href.split('#');
                    const curPath = window.location.pathname === '/' ? '/home' : window.location.pathname;
                    if ((!pathPart || pathPart === curPath) && hashPart) {
                        const targetEl = document.getElementById(hashPart);
                        if (targetEl) {
                            e.preventDefault();
                            if (typeof SavoraApp !== 'undefined' && typeof SavoraApp.scrollToHomeHash === 'function') {
                                SavoraApp.scrollToHomeHash(`#${hashPart}`, true);
                            } else {
                                targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
                            }
                        }
                    }
                }
            });
        });

        msgBox.appendChild(div);
        msgBox.scrollTop = msgBox.scrollHeight;
    };

    const renderWelcomeMessage = () => {
        appendMessage(
            'bot',
            '👋 Welcome to **Savora AI Concierge**!\nI am dedicated exclusively to **Savora Restaurant’s website** (signature menu, dish prices, live table availability, online reservations, vouchers, QR ordering, and contact/feedback).\n\nHow can I help you with Savora today?',
            [
                { label: '🪑 Book a Table', url: '/home#tables-section' },
                { label: '🍽️ Signature Menu', url: '/home#menu' },
                { label: '🎁 Voucher Deals', url: '/home#deals' },
                { label: '✉️ Contact & Feedback', url: '/contact' }
            ]
        );
    };

    const restoreOrWelcome = () => {
        msgBox.innerHTML = '';
        renderWelcomeMessage();
        conversationHistory = loadStoredHistory();
        conversationHistory.forEach((item) => {
            appendMessage(item.role === 'bot' ? 'bot' : 'user', item.text, item.links || [], Boolean(item.blocked), item.meta || null);
        });
    };

    const refreshAiStatus = async () => {
        if (!subtitleEl || typeof SavoraApp === 'undefined') return;
        try {
            const res = await SavoraApp.fetchApi('/api/public/chatbot/status');
            const st = res.data || {};
            if (st.aiActive) {
                subtitleEl.textContent = `🟢 Savora AI • ${st.model || 'gemini-3.8-flash'}`;
                subtitleEl.style.color = '#a5d6a7';
            } else {
                subtitleEl.textContent = '🟡 Savora Local Mode (No Token)';
                subtitleEl.style.color = '#ffe082';
            }
        } catch {
        }
    };

    const clientFallbackReply = (question) => {
        const lower = question.toLowerCase();

        if (/(voucher|deal|discount|promo|offer|khuyến mãi|giảm giá)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🎁 **Active Savora Promotional Vouchers:**\n• **VIPPARTY20 (-20%)**: 20% OFF VIP Suite & group bookings\n• **WELCOME50 (-15%)**: 15% OFF first online table reservation\n• **SAVORA10 (-10%)**: 10% OFF total dine-in bill',
                links: [
                    { label: '🎁 Claim Promotional Deals', url: '/home#deals' },
                    { label: '🪑 Book Table with Deal', url: '/home#tables-section' }
                ]
            };
        }

        if (/(table|seat|available|floor|vip|terrace|zone|capacity|book|reserve|reservation|bàn|đặt bàn)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🪑 **Savora Online Table Booking & Floor Map:**\n• **3 Dining Zones:** 1st Floor (Main Hall), 2nd Floor (Garden Terrace), and VIP Private Suites.\n• **Booking Hours:** 10:30 – 21:00 daily (120 mins dining + 10 mins cleaning turnaround).',
                links: [
                    { label: '🪑 Live Floor Map & Booking', url: '/home#tables-section' },
                    { label: '📅 My Table Bookings', url: '/customer/reservations' }
                ]
            };
        }

        if (/(menu|dish|food|eat|drink|steak|tenderloin|salmon|lobster|hotpot|soup|dessert|price|món|thực đơn)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🍽️ **Savora Chef’s Signature Menu:**\n• **Pan-Seared Norwegian Salmon Salad** — 149,000 VND *(★ Signature)*\n• **Lava-Stone Grilled Australian Tenderloin (250g)** — 389,000 VND *(★ Signature)*\n• **Oven-Baked Spiny Lobster with Mozzarella** — 650,000 VND *(★ Signature)*\n• **Savora Royal Seafood Hotpot Set** — 450,000 VND *(★ Signature)*',
                links: [
                    { label: '🍽️ Browse Full Menu', url: '/home#menu' },
                    { label: '🪑 Reserve a Table', url: '/home#tables-section' }
                ]
            };
        }

        if (/(contact|feedback|email|where|location|address|hour|open|phone|hotline|địa chỉ|liên hệ)/i.test(lower)) {
            return {
                blocked: false,
                answer: '📍 **Savora Location, Opening Hours & Feedback:**\n• **Address:** Hoa Lac Hi-Tech Park, Thach That, Hanoi\n• **Opening Hours:** 10:00 AM – 10:30 PM Daily\n• **Hotline:** 1900 6868 • **Email:** concierge@savora.vn',
                links: [
                    { label: '✉️ Contact & Feedback Page', url: '/contact' },
                    { label: '🏠 Home Page', url: '/home' }
                ]
            };
        }

        return {
            blocked: true,
            answer: '🚫 **Out-of-Scope Question**\nI only answer questions related to the **Savora Restaurant website** (menu, tables, reservations, vouchers, QR ordering, and contact/feedback).',
            links: [
                { label: '🍽️ Signature Menu', url: '/home#menu' },
                { label: '🪑 Book a Table', url: '/home#tables-section' },
                { label: '🎁 Voucher Deals', url: '/home#deals' },
                { label: '✉️ Contact & Feedback', url: '/contact' }
            ]
        };
    };

    const showTypingIndicator = () => {
        const el = document.createElement('div');
        el.className = 'savora-chat-msg savora-chat-msg--bot';
        el.innerHTML = `<div class="savora-chat-typing" aria-label="Savora AI is typing"><span></span><span></span><span></span></div>`;
        msgBox.appendChild(el);
        msgBox.scrollTop = msgBox.scrollHeight;
        return el;
    };

    const sendQuestion = async (questionText) => {
        const q = (questionText || '').trim();
        if (!q || isSending) return;

        isSending = true;
        if (sendBtn) sendBtn.disabled = true;

        const historySnapshot = conversationHistory.slice(-MAX_STORED_TURNS).map((m) => ({
            role: m.role,
            text: m.text
        }));

        appendMessage('user', q);
        conversationHistory.push({ role: 'user', text: q });
        saveStoredHistory();
        if (input) input.value = '';

        const typingEl = showTypingIndicator();

        try {
            const res = await SavoraApp.fetchApi('/api/public/chatbot/ask', {
                method: 'POST',
                body: JSON.stringify({ message: q, history: historySnapshot })
            });
            typingEl.remove();

            const data = res.data || {};
            const answer = data.answer || '';
            const links = data.links || [];
            const usage = data.usage || null;
            let meta = null;
            if (data.mode === 'GEMINI' && usage) {
                meta = { label: `✨ ${data.model || 'gemini'} · ${usage.totalTokens || 0} tokens (${usage.latencyMs || 0} ms)` };
            } else if (data.mode === 'GUARDRAIL') {
                meta = { label: '🛡️ Savora Domain Guardrail' };
            } else if (data.mode === 'FALLBACK') {
                meta = { label: `⚡ ${data.model || 'Local Assistant'}` };
            }

            appendMessage('bot', answer, links, Boolean(data.blocked), meta);
            conversationHistory.push({ role: 'bot', text: answer, links, blocked: Boolean(data.blocked), meta });
            saveStoredHistory();
        } catch (_) {
            typingEl.remove();
            const fb = clientFallbackReply(q);
            const meta = { label: '⚡ Offline Fallback' };
            appendMessage('bot', fb.answer, fb.links, fb.blocked, meta);
            conversationHistory.push({ role: 'bot', text: fb.answer, links: fb.links, blocked: Boolean(fb.blocked), meta });
            saveStoredHistory();
        } finally {
            isSending = false;
            if (sendBtn) sendBtn.disabled = false;
            input?.focus();
        }
    };

    restoreOrWelcome();
    refreshAiStatus();

    launcher.addEventListener('click', () => {
        const isHidden = win.style.display === 'none';
        win.style.display = isHidden ? 'flex' : 'none';
        if (isHidden) {
            refreshAiStatus();
            if (input) input.focus();
        }
    });

    closeBtn?.addEventListener('click', () => {
        win.style.display = 'none';
    });

    clearBtn?.addEventListener('click', () => {
        conversationHistory = [];
        try { sessionStorage.removeItem(HISTORY_KEY); } catch {}
        restoreOrWelcome();
        input?.focus();
    });

    chipsBox?.querySelectorAll('.savora-chat-chip').forEach((chip) => {
        chip.addEventListener('click', () => {
            sendQuestion(chip.dataset.q);
        });
    });

    form?.addEventListener('submit', (e) => {
        e.preventDefault();
        sendQuestion(input?.value || '');
    });
});
