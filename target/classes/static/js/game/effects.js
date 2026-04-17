import { worldToScreen } from "./rendering.js";

const BULLET_SPEED = 40;
const BULLET_LENGTH = 0.22;
const BULLET_THICKNESS = 0.065;
const BULLET_OFFSET = 0.42;
const BULLET_TTL_SECONDS = 0.36;
const BULLET_HIT_RADIUS = 0.35;
const BULLET_WORLD_PADDING = 0.05;

const bullets = [];
let nextBulletId = 1;
const pendingBulletIdsByOwner = new Map();

function createRoundedRectPath(context, x, y, width, height, radius) {
    const cornerRadius = Math.max(0, Math.min(radius, width * 0.5, height * 0.5));

    context.beginPath();
    context.moveTo(x + cornerRadius, y);
    context.lineTo(x + width - cornerRadius, y);
    context.quadraticCurveTo(x + width, y, x + width, y + cornerRadius);
    context.lineTo(x + width, y + height - cornerRadius);
    context.quadraticCurveTo(x + width, y + height, x + width - cornerRadius, y + height);
    context.lineTo(x + cornerRadius, y + height);
    context.quadraticCurveTo(x, y + height, x, y + height - cornerRadius);
    context.lineTo(x, y + cornerRadius);
    context.quadraticCurveTo(x, y, x + cornerRadius, y);
    context.closePath();
}

export function spawnBullet({ x, y, aimDegrees, color = "#ff8a1e", firedBy = "self", ownerToken = null }) {
    const directionRadians = ((aimDegrees ?? 0) * Math.PI) / 180;
    const spawnX = x + Math.cos(directionRadians) * BULLET_OFFSET;
    const spawnY = y + Math.sin(directionRadians) * BULLET_OFFSET;

    const bullet = {
        id: nextBulletId++,
        x: spawnX,
        y: spawnY,
        vx: Math.cos(directionRadians) * BULLET_SPEED,
        vy: Math.sin(directionRadians) * BULLET_SPEED,
        age: 0,
        color: color,
        firedBy: firedBy,
        ownerToken: ownerToken,
        shotId: null
    };
    bullets.push(bullet);

    if (ownerToken) {
        const queue = pendingBulletIdsByOwner.get(ownerToken) ?? [];
        queue.push(bullet.id);
        pendingBulletIdsByOwner.set(ownerToken, queue);
    }
}

export function assignShotIdToLatestBullet(ownerToken, shotId) {
    if (!ownerToken || shotId == null || shotId <= 0) {
        return;
    }

    const queue = pendingBulletIdsByOwner.get(ownerToken);
    if (!queue || queue.length === 0) {
        return;
    }

    while (queue.length > 0) {
        const bulletId = queue.shift();
        const bullet = bullets.find((entry) => entry.id === bulletId);
        if (!bullet) {
            continue;
        }
        if (bullet.shotId == null) {
            bullet.shotId = shotId;
            break;
        }
    }

    if (queue.length === 0) {
        pendingBulletIdsByOwner.delete(ownerToken);
        return;
    }
    pendingBulletIdsByOwner.set(ownerToken, queue);
}

export function advanceBullets(deltaSeconds) {
    if (deltaSeconds <= 0 || bullets.length === 0) {
        return;
    }

    for (const bullet of bullets) {
        bullet.x += bullet.vx * deltaSeconds;
        bullet.y += bullet.vy * deltaSeconds;
        bullet.age += deltaSeconds;
    }

    for (let index = bullets.length - 1; index >= 0; index -= 1) {
        if (bullets[index].age >= BULLET_TTL_SECONDS) {
            bullets.splice(index, 1);
        }
    }
}

export function resolveBulletCollisions(snapshot, selfToken, worldMetrics, onImpact) {
    if (!snapshot || bullets.length === 0) {
        return;
    }

    const self = snapshot.players.find((player) => player.token === selfToken);
    const opponent = snapshot.players.find((player) => player.token !== selfToken);
    if (!opponent || !self) {
        return;
    }

    const arenaMinX = 0 - BULLET_WORLD_PADDING;
    const arenaMinY = 0 - BULLET_WORLD_PADDING;
    const arenaMaxX = (worldMetrics?.boardWidth ?? 0) - 1 + BULLET_WORLD_PADDING;
    const arenaMaxY = (worldMetrics?.boardHeight ?? 0) - 1 + BULLET_WORLD_PADDING;

    for (let index = bullets.length - 1; index >= 0; index -= 1) {
        const bullet = bullets[index];
        const outOfBounds = bullet.x < arenaMinX || bullet.x > arenaMaxX || bullet.y < arenaMinY || bullet.y > arenaMaxY;

        if (outOfBounds) {
            bullets.splice(index, 1);
            continue;
        }

        if (bullet.firedBy === "self") {
            const hitOpponent = Math.hypot(bullet.x - opponent.positionX, bullet.y - opponent.positionY) <= BULLET_HIT_RADIUS;
            if (hitOpponent) {
                if (onImpact) {
                    onImpact({ bullet: { ...bullet }, reason: "hit-opponent", snapshotTick: snapshot.simulationTick });
                }
                bullets.splice(index, 1);
            }
        } else if (bullet.firedBy === "opponent") {
            const hitSelf = Math.hypot(bullet.x - self.positionX, bullet.y - self.positionY) <= BULLET_HIT_RADIUS;
            if (hitSelf) {
                if (onImpact) {
                    onImpact({ bullet: { ...bullet }, reason: "hit-self", snapshotTick: snapshot.simulationTick });
                }
                bullets.splice(index, 1);
            }
        }
    }
}

export function drawBullets(context, cameraX, cameraY, scale, width, height) {
    if (bullets.length === 0) {
        return;
    }

    for (const bullet of bullets) {
        const screen = worldToScreen(bullet.x, bullet.y, cameraX, cameraY, scale, width, height);
        const bulletWidth = Math.max(4, BULLET_LENGTH * scale);
        const bulletHeight = Math.max(3, BULLET_THICKNESS * scale);
        const bulletRotation = Math.atan2(bullet.vy, bullet.vx);

        context.save();
        context.translate(screen.x, screen.y);
        context.rotate(bulletRotation);
        context.shadowColor = `rgba(255, 146, 38, 1.0)`;
        context.shadowBlur = 20;
        context.fillStyle = bullet.color;
        createRoundedRectPath(
            context,
            -bulletWidth * 0.5,
            -bulletHeight * 0.5,
            bulletWidth,
            bulletHeight,
            bulletHeight * 0.5
        );
        context.fill();
        context.restore();
    }
}

export function clearBullets() {
    bullets.length = 0;
    nextBulletId = 1;
    pendingBulletIdsByOwner.clear();
}