import { loadProfile, saveProfile } from "../common.js";

export function navigateToReplayRoom(roomCode, token, cleanup) {
    if (!roomCode || !token) {
        return;
    }

    saveProfile({ ...loadProfile(), roomCode, token, stayInRoom: false });
    cleanup();
    window.location.href = "/room.html";
}

export function navigateToRoom(cleanup) {
    saveProfile({ ...loadProfile(), stayInRoom: true });
    cleanup();
    window.location.href = "/room.html";
}
