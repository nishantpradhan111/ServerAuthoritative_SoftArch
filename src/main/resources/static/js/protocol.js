export const SocketCommand = {
    SUBSCRIBE: "subscribe",
    READY: "ready",
    MOVE: "move",
    FIRE: "fire",
    INPUT: "input",
    SYNC: "sync",
    HIT_CLAIM: "hitClaim",
    REPLAY: "replay",
    RETURN_TO_ROOM: "returnRoom"
};

export const SocketEvent = {
    SNAPSHOT: "snapshot",
    ERROR: "error",
    REPLAY_REDIRECT: "replayRedirect",
    ROOM_RETURN: "roomReturn"
};