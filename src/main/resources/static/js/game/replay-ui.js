export function setReplayStatusBadge(replayStatusBadge, text, tone = "idle") {
    if (!replayStatusBadge) {
        return;
    }

    if (!text) {
        replayStatusBadge.hidden = true;
        replayStatusBadge.classList.remove("pending", "expired", "matched");
        return;
    }

    replayStatusBadge.hidden = false;
    replayStatusBadge.textContent = text;
    replayStatusBadge.classList.remove("pending", "expired", "matched");
    if (tone === "pending" || tone === "expired" || tone === "matched") {
        replayStatusBadge.classList.add(tone);
    }
}
