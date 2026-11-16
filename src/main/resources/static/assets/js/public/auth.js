document.addEventListener('DOMContentLoaded', () => {
    const alertBox = document.getElementById('auth-alert');
    const emailInput = document.getElementById('email');
    const passwordInput = document.getElementById('password');
    const submitBtn = document.getElementById('btn-login-submit');

    const urlParams = new URLSearchParams(window.location.search);
    if ((urlParams.get('error') === 'google_failed' || urlParams.get('error') === 'oauth2_failed') && alertBox) {
        alertBox.textContent = 'Social sign-in failed. Please verify your OAuth2 configuration.';
        alertBox.style.display = 'block';
    }

    document.querySelectorAll('.password-toggle[aria-controls]').forEach(button => {
        const input = document.getElementById(button.getAttribute('aria-controls'));
        button.addEventListener('click', () => {
            const showPassword = input.type === 'password';
            input.type = showPassword ? 'text' : 'password';
            button.setAttribute('aria-pressed', String(showPassword));
        });
    });

    // Forgot password prompt
    const forgotLink = document.getElementById('forgot-password-link');
    if (forgotLink && alertBox) {
        forgotLink.addEventListener('click', () => {
            alertBox.style.background = '#fff8e6';
            alertBox.style.borderColor = '#ffd591';
            alertBox.style.color = '#ad6800';
            alertBox.textContent = 'Please contact Savora Concierge (1900 6868) or your administrator to reset your password.';
            alertBox.style.display = 'block';
        });
    }

    // 1. Handle Sign-In Form Submission
    const loginForm = document.getElementById('login-form');
    if (loginForm) {
        loginForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            resetAlert(alertBox);

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
                showError(alertBox, err.message);
                if (submitBtn) {
                    submitBtn.disabled = false;
                    submitBtn.innerHTML = originalBtnHtml;
                }
            }
        });
    }

    // 2. Handle Sign-Up (Registration) Form Submission
    const registerForm = document.getElementById('register-form');
    const registerBtn = document.getElementById('btn-register-submit');
    if (registerForm) {
        registerForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            resetAlert(alertBox);

            const firstName = document.getElementById('firstName').value.trim();
            const lastName = document.getElementById('lastName').value.trim();
            const email = document.getElementById('reg-email').value.trim();
            const phone = document.getElementById('phone').value.trim();
            const gender = document.getElementById('gender').value;
            const password = document.getElementById('reg-password').value;
            const confirmPassword = document.getElementById('confirmPassword').value;

            if (password !== confirmPassword) {
                showError(alertBox, 'Password confirmation does not match.');
                return;
            }

            const originalBtnHtml = registerBtn ? registerBtn.innerHTML : '';
            if (registerBtn) {
                registerBtn.disabled = true;
                registerBtn.innerHTML = '<span>Creating your account...</span>';
            }

            try {
                const res = await SavoraApp.fetchApi('/api/auth/register', {
                    method: 'POST',
                    body: JSON.stringify({
                        firstName,
                        lastName,
                        email,
                        phone,
                        gender,
                        password,
                        confirmPassword
                    })
                });
                window.location.href = res.data.redirectUrl || '/home';
            } catch (err) {
                showError(alertBox, err.message);
                if (registerBtn) {
                    registerBtn.disabled = false;
                    registerBtn.innerHTML = originalBtnHtml;
                }
            }
        });
    }

    function resetAlert(box) {
        if (!box) return;
        box.style.display = 'none';
        box.style.background = '#fff1f0';
        box.style.borderColor = '#ffa39e';
        box.style.color = '#cf1322';
    }

    function showError(box, message) {
        if (!box) return;
        box.textContent = message;
        box.style.display = 'block';
    }
});
