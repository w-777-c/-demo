"use strict";

const byId = (id) => document.getElementById(id);
const canvas = byId("board");
const context = canvas.getContext("2d");
const widthInput = byId("brush-width");
const pending = new Set();
let socket = null;
let retryTimer, connectTimer, heartbeatTimer;
let retryDelay = 1000;
let manuallyDisconnected = false;
let ready = false;
let seq = 0, epoch = 0, counter = 0;
let clientId = "";
let segments = 0, pointCount = 0, maxSegments = 2000, maxPoints = 20000;
let tool = "pen", color = "#25363b";
let active = null;
let lastReceived = 0;

function error(message = "") {
  byId("board-error").textContent = message;
  byId("board-error").hidden = !message;
}

function updateFooter() {
  const usage = Math.min(100, Math.ceil(Math.max(segments / maxSegments, pointCount / maxPoints) * 100));
  byId("board-capacity").value = usage;
  byId("capacity-label").textContent = `已用 ${usage}%`;
  byId("sync-status").textContent = !ready ? "等待连接" : pending.size ? "同步中…" : "已同步";
  canvas.dataset.seq = String(seq);
}

function setState(state, text) {
  byId("board-status").dataset.state = state;
  byId("board-status-text").textContent = text;
  canvas.setAttribute("aria-disabled", String(!ready));
  byId("clear-board").disabled = !ready;
  const label = manuallyDisconnected ? "重新连接" : "断开连接";
  byId("board-connect").setAttribute("aria-label", label);
  byId("board-connect").title = label;
  updateFooter();
}

function blank() {
  context.fillStyle = "#ffffff";
  context.fillRect(0, 0, canvas.width, canvas.height);
}

function render(segment) {
  const points = segment.points;
  context.strokeStyle = context.fillStyle = segment.color;
  context.lineWidth = segment.width;
  context.lineCap = context.lineJoin = "round";
  if (points.length === 1) {
    context.beginPath();
    context.arc(points[0][0], points[0][1], segment.width / 2, 0, Math.PI * 2);
    context.fill();
  } else {
    context.beginPath();
    context.moveTo(points[0][0], points[0][1]);
    for (const point of points.slice(1)) context.lineTo(point[0], point[1]);
    context.stroke();
  }
}

function send(message) {
  if (socket?.readyState !== WebSocket.OPEN) return false;
  if (socket.bufferedAmount > 65536 || pending.size >= 128) {
    error("连接繁忙，正在重新同步画布。" );
    socket.close();
    return false;
  }
  try { socket.send(JSON.stringify({ v: 1, ...message })); return true; }
  catch { socket.close(); return false; }
}

function stopStroke() {
  if (!active) return;
  const pointerId = active.pointerId;
  clearInterval(active.timer);
  active = null;
  if (canvas.hasPointerCapture(pointerId)) canvas.releasePointerCapture(pointerId);
}

function apply(event) {
  if (event.v !== 1) throw new Error("Unsupported protocol");
  if (event.type === "snapshot") {
    stopStroke();
    blank();
    for (const segment of event.segments) render(segment);
    seq = event.seq;
    epoch = event.epoch;
    clientId = event.clientId;
    maxSegments = event.maxSegments;
    maxPoints = event.maxPoints;
    segments = event.segments.length;
    pointCount = event.segments.reduce((sum, segment) => sum + segment.points.length, 0);
    pending.clear();
    ready = true;
    error();
    retryDelay = 1000;
    clearTimeout(connectTimer);
    setState("connected", "已连接");
    return;
  }
  if (event.type === "presence") { byId("online-count").textContent = String(event.count); return; }
  if (event.type === "pong") return;
  if (event.type === "error") {
    stopStroke();
    pending.clear();
    const errors = { STALE_EPOCH: "画布已更新，正在同步。", BOARD_FULL: "画布容量已满，请导出作品后清空画布。", BAD_MESSAGE: "绘画操作无效，请重新连接。" };
    error(errors[event.code] || "同步失败，请重新连接。" );
    updateFooter();
    return;
  }
  if (event.type === "ack") { pending.delete(event.opId); updateFooter(); return; }
  if (!ready || !["draw", "clear"].includes(event.type)) return;
  if (event.seq <= seq) return;
  if (event.seq !== seq + 1 || (event.type === "draw" && event.epoch !== epoch)) {
    ready = false;
    stopStroke();
    setState("connecting", "同步中");
    send({ type: "sync" });
    return;
  }
  seq = event.seq;
  if (event.type === "clear") {
    epoch = event.epoch;
    stopStroke();
    blank();
    segments = pointCount = 0;
    error();
    if (event.clientId === clientId) pending.delete(event.opId);
  } else {
    render(event.segment);
    segments++;
    pointCount += event.segment.points.length;
    if (event.segment.clientId === clientId) pending.delete(event.segment.opId);
  }
  updateFooter();
}

function connect() {
  clearTimeout(retryTimer);
  ready = false;
  setState("connecting", "连接中");
  const current = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/board`);
  socket = current;
  connectTimer = setTimeout(() => { if (socket === current && !ready) current.close(); }, 8000);
  current.addEventListener("open", () => {
    if (socket !== current) return;
    lastReceived = Date.now();
    heartbeatTimer = setInterval(() => {
      if (Date.now() - lastReceived > 65000) current.close();
      else send({ type: "ping" });
    }, 25000);
  });
  current.addEventListener("message", (message) => {
    if (socket !== current) return;
    lastReceived = Date.now();
    try { apply(JSON.parse(message.data)); }
    catch { error("画布数据异常，正在重新连接。" ); current.close(); }
  });
  current.addEventListener("close", () => {
    if (socket !== current) return;
    socket = null;
    ready = false;
    stopStroke();
    pending.clear();
    clearTimeout(connectTimer);
    clearInterval(heartbeatTimer);
    byId("online-count").textContent = "0";
    setState("idle", manuallyDisconnected ? "已断开" : "重连中");
    if (!manuallyDisconnected) {
      retryTimer = setTimeout(connect, retryDelay);
      retryDelay = Math.min(8000, retryDelay * 2);
    }
  });
}

function disconnect() {
  manuallyDisconnected = true;
  clearTimeout(retryTimer);
  clearTimeout(connectTimer);
  clearInterval(heartbeatTimer);
  ready = false;
  stopStroke();
  pending.clear();
  const previous = socket;
  socket = null;
  if (previous) previous.close(1000, "Client disconnected");
  byId("online-count").textContent = "0";
  setState("idle", "已断开");
}

function position(event) {
  const rect = canvas.getBoundingClientRect();
  return [Math.round(Math.max(0, Math.min(1200, (event.clientX - rect.left) / rect.width * 1200)) * 10) / 10,
    Math.round(Math.max(0, Math.min(720, (event.clientY - rect.top) / rect.height * 720)) * 10) / 10];
}

function flushStroke() {
  if (!active || !active.dirty || !ready) return;
  const opId = String(++counter);
  if (send({ type: "draw", opId, epoch: active.epoch, tool: active.tool, color: active.color, width: active.width, points: active.points })) {
    pending.add(opId);
    active.points = [active.points[active.points.length - 1]];
    active.dirty = false;
    updateFooter();
  } else stopStroke();
}

canvas.addEventListener("pointerdown", (event) => {
  if (!ready || active || !event.isPrimary || event.button !== 0) return;
  event.preventDefault();
  active = { pointerId: event.pointerId, points: [position(event)], dirty: true, tool, color, width: Number(widthInput.value), epoch };
  canvas.setPointerCapture(event.pointerId);
  active.timer = setInterval(flushStroke, 32);
  flushStroke();
});
canvas.addEventListener("pointermove", (event) => {
  if (!active || active.pointerId !== event.pointerId) return;
  const point = position(event);
  const last = active.points[active.points.length - 1];
  if (point[0] === last[0] && point[1] === last[1]) return;
  active.points.push(point);
  active.dirty = true;
  if (active.points.length >= 32) flushStroke();
});
canvas.addEventListener("pointerup", (event) => {
  if (!active || active.pointerId !== event.pointerId) return;
  const point = position(event);
  const last = active.points[active.points.length - 1];
  if (point[0] !== last[0] || point[1] !== last[1]) { active.points.push(point); active.dirty = true; }
  flushStroke();
  stopStroke();
});
canvas.addEventListener("pointercancel", stopStroke);
canvas.addEventListener("lostpointercapture", stopStroke);
window.addEventListener("blur", () => { flushStroke(); stopStroke(); });
for (const button of document.querySelectorAll("[data-tool]")) button.addEventListener("click", () => {
  tool = button.dataset.tool;
  for (const item of document.querySelectorAll("[data-tool]")) item.setAttribute("aria-pressed", String(item === button));
});
for (const button of document.querySelectorAll("[data-color]")) button.addEventListener("click", () => {
  color = button.dataset.color;
  for (const item of document.querySelectorAll("[data-color]")) item.setAttribute("aria-pressed", String(item === button));
  document.querySelector('[data-tool="pen"]').click();
});
widthInput.addEventListener("input", () => { byId("width-value").textContent = widthInput.value; });
byId("board-connect").addEventListener("click", () => {
  if (!manuallyDisconnected) disconnect();
  else { manuallyDisconnected = false; error(); connect(); }
});
byId("clear-board").addEventListener("click", () => {
  if (!ready) return;
  const dialog = byId("clear-dialog");
  dialog.returnValue = "cancel";
  dialog.showModal();
});
byId("clear-dialog").addEventListener("close", () => {
  if (byId("clear-dialog").returnValue !== "clear" || !ready) return;
  stopStroke();
  const opId = String(++counter);
  if (send({ type: "clear", epoch, opId })) { pending.add(opId); updateFooter(); }
});
byId("download-board").addEventListener("click", () => {
  canvas.toBlob((blob) => {
    if (!blob) { error("图片导出失败，请重试。" ); return; }
    const link = document.createElement("a");
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = `iron-arena-canvas-${new Date().toISOString().replace(/[:.]/g, "-")}.png`;
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  }, "image/png");
});
window.addEventListener("pagehide", disconnect);
window.addEventListener("pageshow", (event) => { if (event.persisted) { manuallyDisconnected = false; connect(); } });
blank();
connect();
