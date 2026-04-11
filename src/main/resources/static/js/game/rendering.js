import { clamp } from "./math.js";

function drawRoundedRect(context, x, y, width, height, radius) {
    const cornerRadius = Math.max(0, Math.min(radius, width * 0.5, height * 0.5));
    context.beginPath();
    context.moveTo(x + cornerRadius, y);
    context.arcTo(x + width, y, x + width, y + height, cornerRadius);
    context.arcTo(x + width, y + height, x, y + height, cornerRadius);
    context.arcTo(x, y + height, x, y, cornerRadius);
    context.arcTo(x, y, x + width, y, cornerRadius);
    context.closePath();
}

export function renderBackdrop(context, width, height) {
    const gradient = context.createLinearGradient(0, 0, 0, height);
    gradient.addColorStop(0, "#2a2418");
    gradient.addColorStop(1, "#1a1711");
    context.fillStyle = gradient;
    context.fillRect(0, 0, width, height);

    const haze = context.createRadialGradient(width * 0.5, height * 0.42, 24, width * 0.5, height * 0.42, height * 0.65);
    haze.addColorStop(0, "rgba(209, 194, 154, 0.12)");
    haze.addColorStop(1, "rgba(209, 194, 154, 0)");
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
    context.strokeStyle = "rgba(192, 177, 141, 0.2)";
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
    const labelName = player.name;
    const hpText = `HP ${player.health}`;
    const nameFont = "700 12px Verdana";
    const hpFont = "800 11px Verdana";
    const labelY = screen.y - radius - 34;
    const labelHeight = 28;
    const gap = 8;
    const labelPaddingX = 10;
    const pillPaddingX = 9;

    context.fillStyle = isSelf ? "rgba(209, 194, 154, 0.95)" : "rgba(188, 164, 118, 0.92)";
    context.beginPath();
    context.arc(screen.x, screen.y, radius, 0, Math.PI * 2);
    context.fill();

    if (!isSelf) {
        context.strokeStyle = "rgba(37, 31, 22, 0.95)";
        context.lineWidth = 2;
        context.beginPath();
        context.moveTo(screen.x, screen.y);
        context.lineTo(
            screen.x + Math.cos(facingRadians) * radius * 1.65,
            screen.y + Math.sin(facingRadians) * radius * 1.65
        );
        context.stroke();
    }

    context.save();
    context.textBaseline = "middle";

    context.font = nameFont;
    const nameWidth = context.measureText(labelName).width;
    context.font = hpFont;
    const hpWidth = context.measureText(hpText).width;

    const nameChipWidth = nameWidth + (labelPaddingX * 2);
    const hpChipWidth = hpWidth + (pillPaddingX * 2);
    const totalWidth = nameChipWidth + gap + hpChipWidth;
    const labelX = screen.x - (totalWidth * 0.5);

    context.fillStyle = "rgba(29, 25, 19, 0.9)";
    context.strokeStyle = isSelf ? "rgba(209, 194, 154, 0.34)" : "rgba(188, 164, 118, 0.3)";
    context.lineWidth = 1;
    drawRoundedRect(context, labelX, labelY, totalWidth, labelHeight, 999);
    context.fill();
    context.stroke();

    context.fillStyle = "rgba(245, 237, 220, 0.98)";
    context.font = nameFont;
    context.textAlign = "left";
    context.fillText(labelName, labelX + labelPaddingX, labelY + (labelHeight * 0.5));

    const hpChipX = labelX + nameChipWidth + gap;
    const hpTone = player.health <= 1
        ? "rgba(232, 99, 92, 0.46)"
        : player.health <= 3
            ? "rgba(227, 186, 109, 0.4)"
            : "rgba(126, 228, 148, 0.44)";
    context.fillStyle = hpTone;
    drawRoundedRect(context, hpChipX, labelY + 3, hpChipWidth, labelHeight - 6, 999);
    context.fill();

    context.fillStyle = "rgba(245, 237, 220, 0.98)";
    context.font = hpFont;
    context.fillText(hpText, hpChipX + pillPaddingX, labelY + (labelHeight * 0.5));

    context.restore();
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
    context.strokeStyle = "rgba(219, 201, 161, 0.82)";
    context.shadowColor = "rgba(209, 194, 154, 0.46)";
    context.shadowBlur = 9;
    context.beginPath();
    context.moveTo(fromX, fromY);
    context.lineTo(clampedX, clampedY);
    context.stroke();
    context.restore();
}
