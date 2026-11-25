/**
 * SAVORA — Domain-Locked Customer AI Chatbot with Website Navigation Links
 * Strictly answers ONLY questions about Savora Restaurant and blocks off-topic questions.
 */
document.addEventListener('DOMContentLoaded', () => {
    const launcher = document.getElementById('savora-chat-launcher');
    const win = document.getElementById('savora-chat-window');
    const closeBtn = document.getElementById('savora-chat-close');
    const msgBox = document.getElementById('savora-chat-messages');
    const form = document.getElementById('savora-chat-form');
    const input = document.getElementById('savora-chat-input');
    const chipsBox = document.getElementById('savora-chat-chips');

    if (!launcher || !win || !msgBox) return;

    const formatMarkdownText = (text) => {
        if (!text) return '';
        return String(text)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
            .replace(/\*(.+?)\*/g, '<em>$1</em>')
            .replace(/\n/g, '<br>');
    };

    const appendMessage = (sender, text, links = [], blocked = false) => {
        const div = document.createElement('div');
        div.className = `savora-chat-msg ${sender === 'user' ? 'savora-chat-msg--user' : 'savora-chat-msg--bot'} ${blocked ? 'savora-chat-msg--blocked' : ''}`;

        let html = `<div>${formatMarkdownText(text)}</div>`;
        if (sender === 'bot' && Array.isArray(links) && links.length > 0) {
            html += `<div class="savora-chat-nav-links">` +
                links.map((l) => `<a href="${l.url}" class="savora-chat-nav-link">${l.label} →</a>`).join('') +
                `</div>`;
        }
        div.innerHTML = html;

        // Smooth scroll if clicking an anchor on the current page
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
                            targetEl.scrollIntoView({ behavior: 'smooth', block: 'start' });
                        }
                    }
                }
            });
        });

        msgBox.appendChild(div);
        msgBox.scrollTop = msgBox.scrollHeight;
    };

    // Initial welcome message with navigation links
    appendMessage(
        'bot',
        '👋 Welcome to **Savora Fine Dining AI Concierge**!\nI only answer questions about **Savora Restaurant** (our signature menu, live table availability, online reservations, vouchers, QR ordering, and contact/feedback).\n\nHow can I help you today?',
        [
            { label: '🪑 Book a Table', url: '/home#tables-section' },
            { label: '🍽️ Signature Menu', url: '/home#menu' },
            { label: '🎁 Voucher Deals', url: '/home#deals' },
            { label: '✉️ Contact & Feedback', url: '/contact' }
        ]
    );

    // Client-side fallback classifier in case backend has not been restarted yet
    const clientFallbackReply = (question) => {
        const lower = question.toLowerCase();
        const offTopicRegex = /(\b\d+\s*[+\-*/^]\s*\d+\b|python|javascript|java code|c\+\+|write code|programming|algorithm|bitcoin|crypto|weather|president|politics|football|soccer|world cup|messi|ronaldo|capital of|who invented|physics|chemistry|homework|thời tiết|bóng đá|chính trị|tổng thống|viết code|lập trình|toán học|giải phương trình)/i;

        if (offTopicRegex.test(lower)) {
            return {
                blocked: true,
                answer: '🚫 **Off-Topic Question Blocked**\nI am the **Savora Fine Dining AI Assistant**, and I am strictly configured to **only answer questions about the Savora Restaurant project** (our menu dishes, table availability, online reservations, promotional vouchers, QR ordering, opening hours, and guest feedback).\n\nI cannot answer unrelated questions. Please explore Savora using the links below:',
                links: [
                    { label: '🪑 Book a Table', url: '/home#tables-section' },
                    { label: '🍽️ View Signature Menu', url: '/home#menu' },
                    { label: '🎁 Promotional Deals', url: '/home#deals' },
                    { label: '✉️ Contact & Feedback', url: '/contact' }
                ]
            };
        }

        if (/(voucher|deal|discount|promo|offer|khuyến mãi|giảm giá|mã|ưu đãi)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🎁 **Active Savora Promotional Vouchers:**\n• **VIPPARTY20 (-20%)**: 20% OFF VIP Suite & group bookings\n• **WELCOME50 (-15%)**: 15% OFF first online table reservation\n• **SAVORA10 (-10%)**: 10% OFF total dine-in bill\n\nClick below to apply a voucher directly to your booking:',
                links: [
                    { label: '🎁 Claim Promotional Deals', url: '/home#deals' },
                    { label: '🪑 Book Table with Deal', url: '/home#tables-section' }
                ]
            };
        }

        if (/(table|seat|available|floor|vip|terrace|zone|capacity|book|reserve|reservation|bàn|chỗ|trống|tầng|đặt bàn|lịch đặt|hủy)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🪑 **Savora Online Table Booking & Floor Map:**\n• **3 Dining Zones:** 1st Floor (Main Hall, 2–8 seats), 2nd Floor (Garden Terrace, 6–8 seats), and VIP Private Suites (12–16 seats).\n• **Booking Hours:** 10:30 – 21:00 daily (120 mins dining + 10 mins cleaning turnaround).\n• Select your party size, date, and time slot using the links below:',
                links: [
                    { label: '🪑 Live Floor Map & Booking', url: '/home#tables-section' },
                    { label: '📅 My Table Bookings', url: '/customer/reservations' },
                    { label: '🔐 Sign In', url: '/login' }
                ]
            };
        }

        if (/(menu|dish|food|eat|drink|steak|tenderloin|salmon|lobster|hotpot|soup|dessert|price|món|thực đơn|ăn|giá|cá hồi|bò|tôm hùm|lẩu)/i.test(lower)) {
            return {
                blocked: false,
                answer: '🍽️ **Savora Chef’s Signature Menu:**\n• **Pan-Seared Norwegian Salmon Salad** — 149,000 VND *(★ Signature)*\n• **Lava-Stone Grilled Australian Tenderloin (250g)** — 389,000 VND *(★ Signature)*\n• **Oven-Baked Spiny Lobster with Mozzarella** — 650,000 VND *(★ Signature)*\n• **Savora Royal Seafood Hotpot Set** — 450,000 VND *(★ Signature)*\n\nExplore our full menu or reserve your table below:',
                links: [
                    { label: '🍽️ Browse Full Menu', url: '/home#menu' },
                    { label: '🪑 Reserve a Table', url: '/home#tables-section' }
                ]
            };
        }

        if (/(contact|feedback|email|where|location|address|hour|open|phone|hotline|liên hệ|góp ý|phản hồi|địa chỉ|ở đâu|giờ mở cửa)/i.test(lower)) {
            return {
                blocked: false,
                answer: '📍 **Savora Location, Opening Hours & Feedback:**\n• **Address:** Hoa Lac Hi-Tech Park, Thach That, Hanoi\n• **Opening Hours:** 10:00 AM – 10:30 PM Daily\n• **Hotline:** 1900 6868 • **Email:** concierge@savora.vn\n• Send us your feedback on our Contact page:',
                links: [
                    { label: '✉️ Contact & Feedback Page', url: '/contact' },
                    { label: '🏠 Home Page', url: '/home' }
                ]
            };
        }

        if (/(account|profile|login|sign in|register|sign up|tài khoản|hồ sơ|đăng nhập|đăng ký)/i.test(lower)) {
            return {
                blocked: false,
                answer: '👤 **Savora Customer Account & Profile:**\nManage your personal details, view your booking history, or sign in via the links below:',
                links: [
                    { label: '👤 My Profile', url: '/customer/profile' },
                    { label: '📅 My Bookings', url: '/customer/reservations' },
                    { label: '🔐 Sign In', url: '/login' }
                ]
            };
        }

        if (/(qr|pay|bill|vietqr|mbbank|cash|kitchen|quét mã|thanh toán|hóa đơn)/i.test(lower)) {
            return {
                blocked: false,
                answer: '📱 **Table QR Ordering & Checkout:**\nAt Savora, guests scan the QR code on their table (`/qr/table/{tableCode}`) to order dishes directly to the Chef Kitchen Display System and track status live (`PENDING → COOKING → READY → SERVED`), then pay via **VietQR (MBBank)** or **Cash**.',
                links: [
                    { label: '🍽️ Preview Digital Menu', url: '/home#menu' },
                    { label: '🪑 Book a Table', url: '/home#tables-section' }
                ]
            };
        }

        if (/(hi|hello|hey|help|savora|project|restaurant|about|xin chào|chào|nhà hàng|dự án|giới thiệu)/i.test(lower)) {
            return {
                blocked: false,
                answer: '👋 I am the **Savora Fine Dining AI Concierge**! I can help you explore our signature dishes, check live table availability, apply promotional vouchers, or submit guest feedback.',
                links: [
                    { label: '🪑 Book a Table', url: '/home#tables-section' },
                    { label: '🍽️ Signature Menu', url: '/home#menu' },
                    { label: '🎁 Voucher Deals', url: '/home#deals' },
                    { label: '✉️ Contact & Feedback', url: '/contact' }
                ]
            };
        }

        return {
            blocked: true,
            answer: '🚫 **Off-Topic Question Blocked**\nI am the **Savora Fine Dining AI Assistant**, and I am strictly configured to **only answer questions about the Savora Restaurant project** (our menu dishes, table availability, online reservations, promotional vouchers, QR ordering, opening hours, and guest feedback).\n\nI cannot answer unrelated questions. Please explore Savora using the links below:',
            links: [
                { label: '🪑 Book a Table', url: '/home#tables-section' },
                { label: '🍽️ View Signature Menu', url: '/home#menu' },
                { label: '🎁 Promotional Deals', url: '/home#deals' },
                { label: '✉️ Contact & Feedback', url: '/contact' }
            ]
        };
    };

    const sendQuestion = async (questionText) => {
        const q = (questionText || '').trim();
        if (!q) return;
        appendMessage('user', q);
        if (input) input.value = '';

        try {
            const res = await SavoraApp.fetchApi('/api/public/chatbot/ask', {
                method: 'POST',
                body: JSON.stringify({ message: q })
            });
            const data = res.data || {};
            appendMessage('bot', data.answer, data.links || [], Boolean(data.blocked));
        } catch (_) {
            const fb = clientFallbackReply(q);
            appendMessage('bot', fb.answer, fb.links, fb.blocked);
        }
    };

    launcher.addEventListener('click', () => {
        const isHidden = win.style.display === 'none';
        win.style.display = isHidden ? 'flex' : 'none';
        if (isHidden && input) input.focus();
    });

    closeBtn?.addEventListener('click', () => {
        win.style.display = 'none';
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
