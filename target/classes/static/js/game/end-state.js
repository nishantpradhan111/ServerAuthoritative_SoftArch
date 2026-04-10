const WIN_MESSAGES = [
    "You cooked them so hard the arena asked for the recipe.",
    "Victory secured. Opponent rage-meter: critical.",
    "That aim was illegal in at least three regions.",
    "Clean win. Zero panic, maximum style.",
    "You need to touch grass.",
    "Arena control: yours. Confidence: outrageous.",
    "They brought a pulse. You brought a masterclass.",
    "Mission accomplished with disrespectful efficiency.",
    "You won so fast the replay had to buffer your aura.",
    "Dominant performance. Someone check on their ego."
];

const LOSS_MESSAGES = [
    "Defeat today. Plot armor reload in progress.",
    "You got outplayed, not outclassed. Next round.",
    "Tactical setback. Dramatic comeback pending.",
    "They won this one. You collected useful anger.",
    "Rough round, strong sequel energy.",
    "Temporary L. Permanent menace.",
    "You lost the duel, not the storyline.",
    "The arena humbled you. Briefly.",
    "Opposition popped off. Time to return the favor.",
    "Loss logged. Revenge patch deployed."
];

export function resolveEndPresentation(snapshot, selfToken, previousMatchKey, previousMessage) {
    if (!snapshot || snapshot.phase !== "COMPLETE") {
        return {
            visible: false,
            title: "",
            message: "",
            matchKey: null
        };
    }

    const didWin = snapshot.winnerToken === selfToken;
    const title = didWin ? "Victory" : "Defeat";
    const matchKey = `${snapshot.code}|${snapshot.winnerToken ?? "none"}|${snapshot.simulationTick}`;

    if (previousMatchKey === matchKey && previousMessage) {
        return {
            visible: true,
            title,
            message: previousMessage,
            matchKey
        };
    }

    const pool = didWin ? WIN_MESSAGES : LOSS_MESSAGES;
    const randomIndex = Math.floor(Math.random() * pool.length);
    return {
        visible: true,
        title,
        message: pool[randomIndex],
        matchKey
    };
}
