document.addEventListener('DOMContentLoaded', () => {
    document.querySelectorAll('.password-toggle[aria-controls]').forEach((btn) => {
        const input = document.getElementById(btn.getAttribute('aria-controls'));
        if (!input) return;
        btn.onclick = () => {
            const show = input.type === 'password';
            input.type = show ? 'text' : 'password';
            btn.setAttribute('aria-pressed', String(show));
        };
    });

    const toggle = document.getElementById('theme-toggle');
    if (toggle && window.SavoraTheme) SavoraTheme.bind(toggle);
});
