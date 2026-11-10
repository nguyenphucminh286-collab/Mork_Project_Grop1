document.addEventListener('DOMContentLoaded', () => {
    const alertBox = document.getElementById('auth-alert');
    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('error') === 'google_failed' && alertBox) {
        alertBox.textContent = 'Đăng nhập bằng Google không thành công. Vui lòng kiểm tra lại cấu hình Redirect URI trên Google Cloud Console.';
        alertBox.style.display = 'block';
    }

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (alertBox) alertBox.style.display = 'none';

            const payload = {
                email: document.getElementById('email').value.trim(),
                password: document.getElementById('password').value
            };

            try {
                const res = await SavoraApp.fetchApi('/api/auth/login', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                window.location.href = res.data.redirectUrl || '/home';
            } catch (err) {
                if (alertBox) {
                    alertBox.textContent = err.message;
                    alertBox.style.display = 'block';
                }
            }
        });
    }
});
