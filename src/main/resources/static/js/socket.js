import { wsUrl } from "./common.js";
import { SocketCommand, SocketEvent } from "./protocol.js";

export function openRoomSocket({ roomCode, token, onSnapshot, onError, onReplayRedirect, onRoomReturn, onOpen, onClose }) {
    const socket = new WebSocket(wsUrl("/ws"));

    socket.addEventListener("open", () => {
        socket.send(JSON.stringify({
            type: SocketCommand.SUBSCRIBE,
            roomCode,
            token
        }));
        if (onOpen) {
            onOpen();
        }
    });

    socket.addEventListener("message", (event) => {
        let payload;
        try {
            payload = JSON.parse(event.data);
        } catch {
            return;
        }

        if (payload.type === SocketEvent.SNAPSHOT && onSnapshot) {
            onSnapshot(payload.snapshot);
            return;
        }

        if (payload.type === SocketEvent.ERROR && onError) {
            onError(payload.message ?? "Socket error");
            return;
        }

        if (payload.type === SocketEvent.REPLAY_REDIRECT && onReplayRedirect) {
            onReplayRedirect({
                roomCode: payload.roomCode,
                token: payload.token
            });
            return;
        }

        if (payload.type === SocketEvent.ROOM_RETURN && onRoomReturn) {
            onRoomReturn(payload.message ?? "Opponent returned to room");
        }
    });

    socket.addEventListener("close", () => {
        if (onClose) {
            onClose();
        }
    });

    return {
        socket,
        send(message) {
            if (socket.readyState === WebSocket.OPEN) {
                socket.send(JSON.stringify(message));
            }
        },
        close() {
            socket.close();
        }
    };
}