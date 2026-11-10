document.addEventListener('DOMContentLoaded', () => {
    const alertBox = document.getElementById('auth-alert');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const submitBtn = document.getElementById('btn-login-submit');

    const urlParams = new URLSearchParams(window.location.search);
    if (urlParams.get('error') === 'google_failed' && alertBox) {
        alertBox.textContent = 'Social sign-in failed. Please verify the OAuth2 Redirect URI configuration.';
        alertBox.style.display = 'block';
    }

    // Show / Hide password toggle
    const togglePasswordBtn = document.getElementById('toggle-password');
    if (togglePasswordBtn && passwordInput) {
        togglePasswordBtn.addEventListener('click', () => {
            const isPassword = passwordInput.type === 'password';
            passwordInput.type = isPassword ? 'text' : 'password';
            togglePasswordBtn.style.color = isPassword ? 'var(--primary-color)' : '';
        });
    }

    // 1-Click Quick Demo Account Pills
    const demoPills = document.querySelectorAll('.demo-pill');
    demoPills.forEach((pill) => {
        pill.addEventListener('click', () => {
            demoPills.forEach((p) => p.classList.remove('active'));
            pill.classList.add('active');
            if (emailInput && passwordInput) {
                emailInput.value = pill.dataset.email || '';
                passwordInput.value = pill.dataset.pass || '';
                if (alertBox) alertBox.style.display = 'none';
            }
        });
    });

    // Forgot password demo prompt
    const forgotLink = document.getElementById('forgot-password-link');
    if (forgotLink && alertBox) {
        forgotLink.addEventListener('click', () => {
            alertBox.style.background = '#fff8e6';
            alertBox.style.borderColor = '#ffd591';
            alertBox.style.color = '#ad6800';
            alertBox.textContent = 'Demo Mode: Default password for all sample accounts is 123456.';
            alertBox.style.display = 'block';
        });
    }

    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            if (alertBox) {
                alertBox.style.display = 'none';
                alertBox.style.background = '#fff1f0';
                alertBox.style.borderColor = '#ffa39e';
                alertBox.style.color = '#cf1322';
            }

            const payload = {
                email: emailInput.value.trim(),
                password: passwordInput.value
            };

            const originalBtnHtml = submitBtn ? submitBtn.innerHTML : '';
            if (submitBtn) {
                submitBtn.disabled = true;
                submitBtn.innerHTML = '<span>Signing in...</span>';
            }

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
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }
            }
        });
    }
});
