// ─── Хранение токена ──────────────────────────────────────────────────────────

const TOKEN_KEY  = "rag_token";
const USER_KEY   = "rag_user";

export function getToken()   { return localStorage.getItem(TOKEN_KEY); }
export function getUser()    { try { return JSON.parse(localStorage.getItem(USER_KEY)); } catch { return null; } }

function saveAuth(token, user) {
    localStorage.setItem(TOKEN_KEY, token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearAuth() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
}

// ─── Проверка токена ──────────────────────────────────────────────────────────

async function checkAuth() {
    const token = getToken();
    if (!token) return false;

    try {
        const res = await fetch("/api/auth/me", {
            headers: { "Authorization": "Bearer " + token }
        });
        return res.ok;
    } catch {
        return false;
    }
}

// ─── UI ───────────────────────────────────────────────────────────────────────

const authScreen = document.getElementById("authScreen");
const appShell   = document.getElementById("appShell");
const authForm   = document.getElementById("authForm");
const authEmail  = document.getElementById("authEmail");
const authPass   = document.getElementById("authPassword");
const authError  = document.getElementById("authError");
const authSubmit = document.getElementById("authSubmit");
const tabLogin   = document.getElementById("tabLogin");
const tabReg     = document.getElementById("tabRegister");

const EMAIL_RE = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const SUBMIT_LABEL = { login: "Войти", register: "Зарегистрироваться" };

let mode = "login"; // "login" | "register"

tabLogin.addEventListener("click", () => setMode("login"));
tabReg.addEventListener("click",   () => setMode("register"));

function setMode(m) {
    mode = m;
    tabLogin.classList.toggle("auth-tab--active", m === "login");
    tabReg.classList.toggle("auth-tab--active",   m === "register");
    authSubmit.textContent = SUBMIT_LABEL[m];
    authPass.autocomplete  = m === "login" ? "current-password" : "new-password";
    clearFieldErrors();
    hideError();
}

function showError(msg) {
    authError.textContent = msg;
    authError.classList.remove("is-hidden");
}

function hideError() {
    authError.classList.add("is-hidden");
}

function markInvalid(field) {
    field.classList.add("input--invalid");
}

function clearFieldErrors() {
    authEmail.classList.remove("input--invalid");
    authPass.classList.remove("input--invalid");
}

// Сбрасываем подсветку поля как только пользователь начал её исправлять
authEmail.addEventListener("input", () => authEmail.classList.remove("input--invalid"));
authPass.addEventListener("input",  () => authPass.classList.remove("input--invalid"));

// Проверка полей на клиенте — понятные ошибки до обращения к серверу
function validateForm(email, password) {
    if (!email && !password) {
        markInvalid(authEmail);
        markInvalid(authPass);
        return "Заполните email и пароль";
    }
    if (!email) {
        markInvalid(authEmail);
        return "Введите email";
    }
    if (!EMAIL_RE.test(email)) {
        markInvalid(authEmail);
        return "Некорректный email — пример: name@example.com";
    }
    if (!password) {
        markInvalid(authPass);
        return "Введите пароль";
    }
    if (mode === "register" && password.length < 8) {
        markInvalid(authPass);
        return "Пароль должен быть не менее 8 символов";
    }
    if (password.length > 128) {
        markInvalid(authPass);
        return "Пароль слишком длинный";
    }
    return null;
}

authForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    hideError();
    clearFieldErrors();

    const email    = authEmail.value.trim();
    const password = authPass.value;

    const validationError = validateForm(email, password);
    if (validationError) {
        showError(validationError);
        return;
    }

    authSubmit.disabled = true;
    authSubmit.textContent = "...";

    try {
        const url = mode === "login" ? "/api/auth/login" : "/api/auth/register";
        const res = await fetch(url, {
            method:  "POST",
            headers: { "Content-Type": "application/json" },
            body:    JSON.stringify({ email, password })
        });

        const data = await res.json();

        if (!res.ok) {
            showError(data.error || "Ошибка авторизации");
            markInvalid(authEmail);
            markInvalid(authPass);
            return;
        }

        saveAuth(data.token, { userId: data.userId, email: data.email });
        showApp();

    } catch (err) {
        showError("Ошибка сети. Попробуйте ещё раз.");
    } finally {
        authSubmit.disabled = false;
        authSubmit.textContent = SUBMIT_LABEL[mode];
    }
});

function showApp() {
    authScreen.classList.add("is-hidden");
    appShell.classList.remove("is-hidden");
    // Запускаем основное приложение динамически после авторизации
    import("/js/app.js");
}

function showAuthScreen() {
    authScreen.classList.remove("is-hidden");
    appShell.classList.add("is-hidden");
}

// ─── Кнопка выхода ────────────────────────────────────────────────────────────

export function logout() {
    clearAuth();
    location.reload();
}

// ─── Инициализация ────────────────────────────────────────────────────────────

(async () => {
    const valid = await checkAuth();
    if (valid) {
        showApp();
    } else {
        clearAuth();
        showAuthScreen();
    }
})();
