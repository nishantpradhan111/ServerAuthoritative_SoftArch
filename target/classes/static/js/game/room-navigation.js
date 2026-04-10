import { loadProfile, saveProfile } from "../common.js";

export function navigateToReplayRoom(roomCode, token, cleanup) {
    if (!roomCode || !token) {
        return;
    }

    saveProfile({ ...loadProfile(), roomCode, token });
    cleanup();
    window.location.href = "/room.html";
}

export function navigateToRoom(cleanup) {
    cleanup();
    window.location.href = "/room.html";
}
