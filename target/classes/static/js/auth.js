import { loadProfile, saveProfile, apiJson } from "./common.js";

// Tab switching
const loginTab = document.querySelector('[data-tab="login"]');
const registerTab = document.querySelector('[data-tab="register"]');
const loginForm = document.getElementById("login-form");
const registerForm = document.getElementById("register-form");
const authTabs = document.querySelector(".auth-tabs");

loginTab?.addEventListener("click", () => switchTab("login"));
registerTab?.addEventListener("click", () => switchTab("register"));

document.querySelectorAll(".password-toggle").forEach((toggle) => {
    toggle.addEventListener("click", () => {
        const targetId = toggle.getAttribute("data-target");
        const input = targetId ? document.getElementById(targetId) : null;
        if (!input) {
            return;
        }

        const showPassword = input.type === "password";
        input.type = showPassword ? "text" : "password";
        toggle.textContent = showPassword ? "Hide" : "Show";
        toggle.setAttribute("aria-label", showPassword ? "Hide password" : "Show password");
    });
});

window.requestAnimationFrame(() => {
    document.body.classList.add("page-ready");
});

document.querySelectorAll(".stat-value[data-target]").forEach((el) => {
    const target = Number(el.getAttribute("data-target"));
    if (!Number.isFinite(target) || target <= 0) {
        return;
    }

    const start = performance.now();
    const durationMs = 900;
    const tick = (now) => {
        const progress = Math.min(1, (now - start) / durationMs);
        el.textContent = String(Math.round(progress * target));
        if (progress < 1) {
            window.requestAnimationFrame(tick);
        }
    };
    window.requestAnimationFrame(tick);
});

function switchTab(tab) {
    // Update tab buttons
    document.querySelectorAll(".auth-tab").forEach(btn => {
        btn.classList.toggle("active", btn.dataset.tab === tab);
    });

    // Update forms visibility
    document.querySelectorAll(".auth-form").forEach(form => {
        form.classList.toggle("active", form === (tab === "login" ? loginForm : registerForm));
    });

    // Clear previous messages
    document.getElementById("login-message").textContent = "";
    document.getElementById("register-message").textContent = "";

    if (authTabs) {
        authTabs.style.setProperty("--tab-index", tab === "register" ? "1" : "0");
    }
}

switchTab("login");

// Login form handler
loginForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const messageEl = document.getElementById("login-message");

    try {
        messageEl.textContent = "Logging in...";
        messageEl.className = "form-message";

        const username = document.getElementById("login-username").value.trim();
        const password = document.getElementById("login-password").value;

        const result = await apiJson("/api/auth/login", {
            method: "POST",
            body: JSON.stringify({ username, password })
        });

        const issuedToken = String(result.accessToken ?? result.authToken ?? result.token ?? "").trim();
        if (!issuedToken) {
            throw new Error("Login succeeded but no access token was returned. Please try again.");
        }

        // Save user profile and redirect
        const profile = {
            userId: result.userId,
            username: result.username,
            email: result.email,
            authToken: issuedToken,
            accessToken: issuedToken,
            token: issuedToken
        };
        saveProfile(profile);

        messageEl.textContent = "✓ Login successful. Redirecting...";
        messageEl.className = "form-message success";

        // Redirect to room lobby after brief delay
        setTimeout(() => {
            window.location.href = "/room.html";
        }, 800);
    } catch (error) {
        messageEl.textContent = `✗ ${error.message}`;
        messageEl.className = "form-message error";
    }
});

// Register form handler
registerForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const messageEl = document.getElementById("register-message");

    try {
        messageEl.textContent = "Creating account...";
        messageEl.className = "form-message";

        const username = document.getElementById("register-username").value.trim();
        const email = document.getElementById("register-email").value.trim();
        const password = document.getElementById("register-password").value;

        const result = await apiJson("/api/auth/register", {
            method: "POST",
            body: JSON.stringify({ username, email, password })
        });

        const issuedToken = String(result.accessToken ?? result.authToken ?? result.token ?? "").trim();
        if (!issuedToken) {
            throw new Error("Registration succeeded but no access token was returned. Please try logging in.");
        }

        messageEl.textContent = "✓ Account created! Logging you in...";
        messageEl.className = "form-message success";

        // Auto-login and redirect
        setTimeout(() => {
            const profile = {
                userId: result.userId,
                username: result.username,
                email: result.email,
                authToken: issuedToken,
                accessToken: issuedToken,
                token: issuedToken
            };
            saveProfile(profile);
            window.location.href = "/room.html";
        }, 1000);
    } catch (error) {
        messageEl.textContent = `✗ ${error.message}`;
        messageEl.className = "form-message error";
    }
});
