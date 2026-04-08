import { wsUrl } from "./common.js";

export function openRoomSocket({ roomCode, token, onSnapshot, onError, onOpen, onClose }) {
    const socket = new WebSocket(wsUrl("/ws"));

    socket.addEventListener("open", () => {
        socket.send(JSON.stringify({
            type: "subscribe",
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

        if (payload.type === "snapshot" && onSnapshot) {
            onSnapshot(payload.snapshot);
            return;
        }

        if (payload.type === "error" && onError) {
            onError(payload.message ?? "Socket error");
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