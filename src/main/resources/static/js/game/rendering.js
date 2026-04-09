import { clamp } from "./math.js";

export function renderBackdrop(context, width, height) {
    const gradient = context.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, "#0b1c2f");
    gradient.addColorStop(1, "#06101d");
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);

    const haze = context.createRadialGradient(width * 0.5, height * 0.42, 24, width * 0.5, height * 0.42, height * 0.65);
    haze.addColorStop(0, "rgba(86, 230, 242, 0.12)");
    haze.addColorStop(1, "rgba(86, 230, 242, 0)");
    context.fillStyle = haze;
    context.fillRect(0, 0, width, height);
}

export function worldToScreen(worldX, worldY, cameraX, cameraY, scale, width, height) {
    return {
        x: (worldX - cameraX) * scale + width * 0.5,
        y: (worldY - cameraY) * scale + height * 0.5
    };
}

export function drawArenaGrid(context, cameraX, cameraY, scale, width, height, worldMetrics) {
    context.strokeStyle = "rgba(160, 190, 220, 0.17)";
    context.lineWidth = 1;

    for (let x = 0; x <= worldMetrics.boardWidth - 1; x++) {
        const a = worldToScreen(x, 0, cameraX, cameraY, scale, width, height);
        const b = worldToScreen(x, worldMetrics.boardHeight - 1, cameraX, cameraY, scale, width, height);
        context.beginPath();
        context.moveTo(a.x, a.y);
        context.lineTo(b.x, b.y);
        context.stroke();
    }

    for (let y = 0; y <= worldMetrics.boardHeight - 1; y++) {
        const a = worldToScreen(0, y, cameraX, cameraY, scale, width, height);
        const b = worldToScreen(worldMetrics.boardWidth - 1, y, cameraX, cameraY, scale, width, height);
        context.beginPath();
        context.moveTo(a.x, a.y);
        context.lineTo(b.x, b.y);
        context.stroke();
    }
}

export function drawPlayer(context, player, isSelf, cameraX, cameraY, scale, width, height, playerRadius) {
    const screen = worldToScreen(player.positionX, player.positionY, cameraX, cameraY, scale, width, height);
    const radius = Math.max(7, playerRadius * scale);
    const facingRadians = (player.aimDegrees * Math.PI) / 180;

    context.fillStyle = isSelf ? "rgba(86, 230, 242, 0.95)" : "rgba(255, 142, 93, 0.92)";
    context.beginPath();
    context.arc(screen.x, screen.y, radius, 0, Math.PI * 2);
    context.fill();

    if (!isSelf) {
        context.strokeStyle = "rgba(4, 16, 30, 0.95)";
        context.lineWidth = 2;
        context.beginPath();
        context.moveTo(screen.x, screen.y);
        context.lineTo(
            screen.x + Math.cos(facingRadians) * radius * 1.65,
            screen.y + Math.sin(facingRadians) * radius * 1.65
        );
        context.stroke();
    }

    context.fillStyle = "rgba(239, 246, 255, 0.95)";
    context.font = "600 12px Verdana";
    context.textAlign = "center";
    context.fillText(player.name, screen.x, screen.y - radius - 10);
}

export function drawAimTracer(context, fromX, fromY, cameraX, cameraY, scale, width, height, worldMetrics, aimCursorX, aimCursorY) {
    const topLeft = worldToScreen(0, 0, cameraX, cameraY, scale, width, height);
    const bottomRight = worldToScreen(worldMetrics.boardWidth - 1, worldMetrics.boardHeight - 1, cameraX, cameraY, scale, width, height);
    const minX = Math.min(topLeft.x, bottomRight.x);
    const maxX = Math.max(topLeft.x, bottomRight.x);
    const minY = Math.min(topLeft.y, bottomRight.y);
    const maxY = Math.max(topLeft.y, bottomRight.y);

    const clampedX = clamp(aimCursorX, minX, maxX);
    const clampedY = clamp(aimCursorY, minY, maxY);

    context.save();
    context.setLineDash([4, 8]);
    context.lineDashOffset = -((performance.now() / 24) % 12);
    context.lineWidth = 2;
    context.strokeStyle = "rgba(146, 244, 255, 0.82)";
    context.shadowColor = "rgba(86, 230, 242, 0.46)";
    context.shadowBlur = 9;
    context.beginPath();
    context.moveTo(fromX, fromY);
    context.lineTo(clampedX, clampedY);
    context.stroke();
    context.restore();
}