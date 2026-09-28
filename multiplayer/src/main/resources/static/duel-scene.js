"use strict";
(() => {
  const canvas = document.getElementById("duel-scene"), g = canvas.getContext("2d");
  const reduceMotion = matchMedia("(prefers-reduced-motion: reduce)");
  let sceneState = null, animation = null, width = 1000, height = 285;
  new ResizeObserver(() => {
    const rect = canvas.getBoundingClientRect(), ratio = Math.min(devicePixelRatio || 1, 2);
    width = rect.width; height = rect.height;
    canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
    g.setTransform(ratio, 0, 0, ratio, 0, 0);
  }).observe(canvas);
  document.addEventListener("duel-state", ({ detail: { state, previous } }) => {
    if (previous && previous.matchId === state.matchId && state.revision > previous.revision) {
      const changes = [0, 1].map((seat) => (state.fighters.find((p) => p.seat === seat)?.hp ?? 0) - (previous.fighters.find((p) => p.seat === seat)?.hp ?? 0));
      if (changes.some((hp) => hp !== 0)) animation = { started: performance.now(), actor: previous.activeSeat, changes };
    } else if (previous?.matchId !== state.matchId) animation = null;
    sceneState = state;
  });
  function rect(color, x, y, w, h) { g.fillStyle = color; g.fillRect(x, y, w, h); }
  function fighter(x, ground, scale, flipped, alive, armor, dark, light, present) {
    g.save();
    g.globalAlpha = present ? 1 : .32;
    g.translate(x, ground);
    rect("#101514", -17 * scale, 0, 35 * scale, 2 * scale);
    g.scale(flipped ? -scale : scale, scale);
    if (!alive) {
      rect(dark, -22, -5, 14, 5); rect(armor, -12, -8, 17, 8); rect("#aebcba", 5, -7, 9, 7);
      rect("#dbb494", 7, -5, 7, 3); rect("#d2c184", 17, -5, 2, 5); rect("#cad5ca", 19, -3, 10, 2);
    } else {
      rect(dark, -12, -30, 9, 21); rect("#34403e", -8, -15, 7, 14); rect("#34403e", 3, -15, 7, 14);
      rect("#99aaa3", -10, -3, 9, 3); rect("#99aaa3", 3, -3, 10, 3);
      rect(dark, -8, -29, 18, 15); rect(armor, -7, -29, 15, 12); rect(armor, -12, -29, 7, 6); rect(armor, 9, -28, 6, 7);
      rect(light, -6, -28, 3, 10); rect(light, 0, -26, 6, 2);
      rect("#dbb494", -4, -39, 11, 10); rect("#dbb494", 12, -22, 5, 5);
      rect("#b5c2bb", -6, -42, 14, 5); rect("#a0b0a7", -7, -39, 4, 10); rect("#a0b0a7", 7, -39, 3, 7);
      rect(armor, -3, -45, 5, 4); rect("#1c2926", 3, -36, 4, 2); rect("#1c2926", -8, -16, 18, 3);
      rect("#d2c184", -1, -16, 4, 3); rect("#d2c184", 17, -23, 8, 2);
      rect("#d9e4de", 20, -41, 3, 18); rect("#d9e4de", 21, -44, 2, 3); rect("#748c80", 20, -20, 3, 5);
    }
    g.restore();
  }
  function draw(now) {
    const ground = height - 30;
    rect("#191f20", 0, 0, width, height);
    g.strokeStyle = "#293330"; g.lineWidth = 1;
    for (let y = 18; y < ground; y += 36) {
      g.beginPath(); g.moveTo(0, y); g.lineTo(width, y); g.stroke();
      for (let x = Math.floor(y / 36) % 2 * 45; x < width; x += 90) { g.beginPath(); g.moveTo(x, y); g.lineTo(x, y + 36); g.stroke(); }
    }
    const gate = Math.min(170, width * .22);
    rect("#35413c", width / 2 - gate / 2 - 6, 18, gate + 12, ground - 18);
    rect("#121b18", width / 2 - gate / 2, 24, gate, ground - 24);
    for (let x = width / 2 - gate / 2 + 13; x < width / 2 + gate / 2; x += 21) rect("#27352e", x, 24, 3, ground - 24);
    rect("#303d36", 0, ground, width, 30); rect("#68705a", 0, ground, width, 2);
    for (const x of [20, width - 33]) {
      rect("#38463e", x, 28, 13, ground - 28); rect("#c8af65", x + 4, 65, 5, 18);
      rect("#e9bd69", x + 2, 59, 9, 10); rect("#f5d9a0", x + 4, 55 + (reduceMotion.matches ? 0 : Math.floor(now / 170) % 3), 4, 11);
    }
    const progress = animation ? (now - animation.started) / 700 : 2;
    const scale = Math.min(4.3, (height - 45) / 46, width / 125);
    for (let seat = 0; seat < 2; seat++) {
      const player = sceneState?.fighters.find((p) => p.seat === seat);
      const direction = seat === 0 ? 1 : -1;
      const lunge = !reduceMotion.matches && animation?.actor === seat && progress < 1 ? Math.sin(progress * Math.PI) * 18 * direction : 0;
      const x = width * (seat === 0 ? .25 : .75) + lunge;
      const bob = reduceMotion.matches ? 0 : Math.sin(now / 300 + seat) * 1.5;
      fighter(x, ground - 3 - bob, scale, seat === 1, !player || player.hp > 0, seat === 0 ? "#60b79a" : "#d2836c", seat === 0 ? "#2c6555" : "#854d41", seat === 0 ? "#9fd4b8" : "#efb197", !!player);
      if (player?.guarding && player.hp > 0) {
        g.strokeStyle = seat === 0 ? "#9cdcc0" : "#efbd94"; g.lineWidth = 2;
        g.strokeRect(x - scale * 17, ground - scale * 37, scale * 34, scale * 34);
      }
      if (animation && progress < 1 && animation.changes[seat]) {
        const value = animation.changes[seat];
        g.fillStyle = value > 0 ? "#97dfb1" : "#ffc3ad";
        g.font = 'bold 17px "Segoe UI", sans-serif'; g.textAlign = "center";
        g.fillText(`${value > 0 ? "+" : ""}${value}`, x, Math.max(25, ground - scale * 47) - progress * 18);
      }
    }
    requestAnimationFrame(draw);
  }
  requestAnimationFrame(draw);
})();
