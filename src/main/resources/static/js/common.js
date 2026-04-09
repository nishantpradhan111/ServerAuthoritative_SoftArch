const PROFILE_KEY = "codereboot.profile";

export function loadProfile() {
    const sessionRaw = sessionStorage.getItem(PROFILE_KEY);
    if (sessionRaw) {
        try {
            return JSON.parse(sessionRaw);
        } catch {
            sessionStorage.removeItem(PROFILE_KEY);
            return {};
        }
    }

    // One-time migration path for older profiles stored in localStorage.
    const legacyRaw = localStorage.getItem(PROFILE_KEY);
    if (!legacyRaw) {
        return {};
    }

    try {
        const profile = JSON.parse(legacyRaw);
        sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
        localStorage.removeItem(PROFILE_KEY);
        return profile;
    } catch {
        localStorage.removeItem(PROFILE_KEY);
        return {};
    }
}

export function saveProfile(profile) {
    sessionStorage.setItem(PROFILE_KEY, JSON.stringify(profile));
}

export function clearProfile() {
    sessionStorage.removeItem(PROFILE_KEY);
    localStorage.removeItem(PROFILE_KEY);
}

export function ensureProfile() {
    const profile = loadProfile();
    if (!profile.userId && !profile.name) {
        window.location.href = "/login.html";
        return null;
    }
    return profile;
}

export async function apiJson(path, options = {}) {
    const headers = new Headers(options.headers ?? {});
    const body = options.body;
    if (body && !(body instanceof FormData)) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(path, {
        ...options,
        headers
    });

    if (!response.ok) {
        let message = "Request failed";
        try {
            const payload = await response.json();
            message = payload.message ?? message;
        } catch {
            message = await response.text() || message;
        }
        throw new Error(message);
    }

    if (response.status === 204) {
        return null;
    }

    return response.json();
}

export function wsUrl(path = "/ws") {
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    return `${protocol}//${window.location.host}${path}`;
}

export function badgeText(value) {
    return String(value ?? "").replace(/_/g, " ").toLowerCase();
}