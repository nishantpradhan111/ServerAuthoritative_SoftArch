export function clamp(value, min, max) {
    return Math.max(min, Math.min(max, value));
}

export function normalizeDegrees(value) {
    const normalized = value % 360;
    return normalized < 0 ? normalized + 360 : normalized;
}

export function blend(current, target, factor) {
    return current + (target - current) * factor;
}

export function distance2D(a, b) {
    return Math.hypot(a.x - b.x, a.y - b.y);
}

export function normalizeVector(x, y) {
    const length = Math.hypot(x, y);
    if (length <= Number.EPSILON) {
        return { x: 0, y: 0 };
    }
    return { x: x / length, y: y / length };
}