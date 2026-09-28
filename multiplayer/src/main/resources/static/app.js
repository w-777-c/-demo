"use strict";

const byId = (id) => document.getElementById(id);
const endpoint = byId("endpoint");
const input = byId("message");
const messages = byId("messages");
const encoder = new TextEncoder();
const maxBytes = 4096;
const maxLogEntries = 200;
const stateNames = { idle: "未连接", connecting: "连接中", connected: "已连接", error: "连接失败" };
let socket = null;
let connectionTimer;
let sent = 0;
let received = 0;
let pending = [];

endpoint.value = `${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/echo`;

function setError(id, message) {
  const element = byId(id);
  element.textContent = message;
  element.hidden = !message;
}

function updateMessageInput() {
  const bytes = encoder.encode(input.value).length;
  const oversized = bytes > maxBytes;
  byId("byte-count").textContent = `${bytes} / ${maxBytes} 字节`;
  byId("byte-count").classList.toggle("over-limit", oversized);
  input.setAttribute("aria-invalid", String(oversized));
  byId("send").disabled = socket?.readyState !== WebSocket.OPEN || oversized;
  setError("message-error", oversized ? "消息超过 4096 字节。" : "");
}

function setState(state) {
  byId("status").dataset.state = state;
  byId("status-text").textContent = stateNames[state];
  byId("detail-status").textContent = stateNames[state];
  byId("connect-label").textContent = state === "connecting" ? "取消" : state === "connected" ? "断开" : "连接";
  endpoint.disabled = state === "connecting" || state === "connected";
  if (state !== "connected") {
    byId("connected-at").textContent = "--";
    byId("latency").textContent = "--";
  }
  updateMessageInput();
}

function appendMessage(kind, text) {
  const followLatest = messages.scrollHeight - messages.scrollTop - messages.clientHeight < 48;
  const row = document.createElement("li");
  row.className = "message-row";
  row.dataset.kind = kind;
  const meta = document.createElement("div");
  meta.className = "message-meta";
  const dot = document.createElement("span");
  dot.className = `legend-dot ${kind}-dot`;
  const label = document.createElement("span");
  label.className = "message-kind";
  label.textContent = { sent: "发送", received: "接收", system: "连接" }[kind];
  const time = document.createElement("time");
  const now = new Date();
  time.dateTime = now.toISOString();
  time.textContent = now.toLocaleTimeString("zh-CN", { hour12: false });
  const body = document.createElement("p");
  body.className = "message-body";
  body.textContent = text;
  meta.append(dot, label, time);
  row.append(meta, body);
  messages.append(row);
  while (messages.children.length > maxLogEntries) messages.firstElementChild.remove();
  byId("empty-log").hidden = true;
  byId("log-count").textContent = String(messages.children.length);
  if (followLatest) messages.scrollTop = messages.scrollHeight;
}

function disconnect() {
  const previous = socket;
  socket = null;
  clearTimeout(connectionTimer);
  pending = [];
  if (previous) {
    const wasConnecting = previous.readyState === WebSocket.CONNECTING;
    previous.close(1000, "Client disconnected");
    appendMessage("system", wasConnecting ? "连接已取消" : "连接已断开");
  }
  setState("idle");
}

byId("connection-form").addEventListener("submit", (event) => {
  event.preventDefault();
  setError("connection-error", "");
  if (socket) {
    disconnect();
    return;
  }
  let address;
  try {
    address = new URL(endpoint.value.trim());
    if (!["ws:", "wss:"].includes(address.protocol) || address.hash || address.username || address.password) throw new Error();
  } catch {
    setError("connection-error", "请输入有效的 ws:// 或 wss:// 地址。" );
    return;
  }
  let current;
  try {
    current = new WebSocket(address.href);
  } catch {
    setState("error");
    setError("connection-error", "无法创建连接，请检查服务地址及浏览器限制。" );
    return;
  }
  socket = current;
  pending = [];
  setState("connecting");
  appendMessage("system", `正在连接 ${address.href}`);
  // Each callback belongs to one socket; closed connections cannot overwrite a new one.
  connectionTimer = setTimeout(() => {
    if (socket !== current) return;
    disconnect();
    setState("error");
    setError("connection-error", "连接超时，请检查服务是否启动。" );
  }, 8000);
  current.addEventListener("open", () => {
    if (socket !== current) return;
    clearTimeout(connectionTimer);
    setState("connected");
    byId("connected-at").textContent = new Date().toLocaleTimeString("zh-CN", { hour12: false });
    appendMessage("system", "连接已建立");
  });
  current.addEventListener("message", (event) => {
    if (socket !== current || typeof event.data !== "string") return;
    received += 1;
    byId("received-count").textContent = String(received);
    const request = pending.shift();
    if (request && request.text === event.data) {
      byId("latency").textContent = Math.max(0, performance.now() - request.started).toFixed(1);
    }
    appendMessage("received", event.data);
  });
  current.addEventListener("error", () => {
    if (socket !== current) return;
    setError("connection-error", "连接出错，请检查地址、服务状态及同源限制。" );
  });
  current.addEventListener("close", (event) => {
    if (socket !== current) return;
    clearTimeout(connectionTimer);
    socket = null;
    pending = [];
    const normal = event.code === 1000 || event.code === 1001;
    setState(normal ? "idle" : "error");
    if (!normal) setError("connection-error", `连接已关闭（${event.code}），可重新连接。`);
    appendMessage("system", `连接已关闭（${event.code}）${event.reason ? `：${event.reason}` : ""}`);
  });
});

byId("message-form").addEventListener("submit", (event) => {
  event.preventDefault();
  updateMessageInput();
  if (byId("send").disabled) return;
  if (socket.bufferedAmount > 65536 || pending.length >= 200) {
    setError("message-error", "待发送或待接收消息过多，请稍后重试。" );
    return;
  }
  try {
    socket.send(input.value);
    pending.push({ text: input.value, started: performance.now() });
    sent += 1;
    byId("sent-count").textContent = String(sent);
    appendMessage("sent", input.value);
  } catch {
    setError("message-error", "发送失败，请重新连接后重试。" );
  }
});

input.addEventListener("input", updateMessageInput);
input.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && (event.ctrlKey || event.metaKey)) {
    event.preventDefault();
    byId("message-form").requestSubmit();
  }
});
byId("clear").addEventListener("click", () => {
  messages.replaceChildren();
  byId("empty-log").hidden = false;
  byId("log-count").textContent = "0";
});
window.addEventListener("pagehide", () => { if (socket) disconnect(); });
setState("idle");
