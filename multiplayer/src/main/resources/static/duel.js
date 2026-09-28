"use strict";
const $ = (id) => document.getElementById(id);
let state = null, seat = -1, socket = null, connected = false, halted = false;
let pending = null, counter = 0, retryDelay = 1000, clockOffset = 0;
let retryTimer, connectionTimer, heartbeatTimer, commandTimer;
let lastReceived = 0, confirmType = "", confirmMatch = "";
let resumeToken = "";
try { resumeToken = sessionStorage.getItem("iron-arena-duel-seat") || ""; } catch { /* In-memory reconnect still works when storage is unavailable. */ }
const errorMessages = {
  BAD_MESSAGE: "操作格式无效，请重新连接。", BAD_NAME: "昵称需为 1–16 位文字、数字、空格、下划线或连字符。",
  ARENA_FULL: "两席已满，可以继续观战。", NOT_SEATED: "请先加入对战。", NOT_WAITING: "对局已经开始。",
  ALREADY_SEATED: "你已经占有席位。", ALREADY_READY: "已确认，正在等待对方。", NOT_FIGHTING: "当前不能进行战斗行动。",
  NOT_YOUR_TURN: "尚未轮到你行动。", STALE_TURN: "回合已更新，请重新选择行动。", STALE_MATCH: "对局已更新，请重新确认。",
  SKILL_UNAVAILABLE: "该技能目前不可用。", BAD_ACTION: "未知战斗行动。", REMATCH_UNAVAILABLE: "双方在线且本局结束后才能再战。",
  SEAT_EXPIRED: "原席位已失效，请重新加入。", HELLO_REQUIRED: "连接尚未就绪。"
};
function error(text = "") { $("duel-error").textContent = text; $("duel-error").hidden = !text; }
function remember(token) {
  resumeToken = token;
  try { if (token) sessionStorage.setItem("iron-arena-duel-seat", token); else sessionStorage.removeItem("iron-arena-duel-seat"); } catch { /* Keep the live token in memory. */ }
}
function fighter(index) { return state?.fighters.find((player) => player.seat === index); }
function phaseText() {
  if (!state) return "等待连接";
  if (state.phase === "WAITING") return state.fighters.length < 2 ? "等待对手加入" : "等待双方准备";
  if (state.phase === "PAUSED") {
    const offline = state.fighters.filter((player) => !player.connected);
    const seconds = Math.max(0, Math.ceil((Math.min(...offline.map((p) => p.reconnectUntil)) - Date.now() - clockOffset) / 1000));
    return `等待断线玩家重连 · ${seconds} 秒`;
  }
  if (state.phase === "FINISHED") return state.winner < 0 ? "本局平局" : `${fighter(state.winner)?.name || "对手"}获胜`;
  return `${fighter(state.activeSeat)?.name || "玩家"}的回合`;
}
function renderControls() {
  const me = fighter(seat);
  const usable = connected && !pending;
  $("identity").textContent = seat < 0 ? "观战中" : `P${seat + 1} · ${me?.name || "你"}`;
  $("join-form").hidden = seat >= 0;
  $("join").disabled = !usable || (state?.fighters.length ?? 2) >= 2;
  $("join").textContent = state?.fighters.length === 2 ? "席位已满" : "加入对战";
  $("ready").hidden = seat < 0 || state?.phase !== "WAITING";
  $("ready").disabled = !usable || !!me?.ready;
  $("ready").querySelector("span").textContent = me?.ready ? "已准备" : "准备";
  $("rematch").hidden = seat < 0 || state?.phase !== "FINISHED";
  $("rematch").disabled = !usable || !!me?.rematch || state?.fighters.length !== 2 || state.fighters.some((p) => !p.connected);
  $("rematch").querySelector("span").textContent = me?.rematch ? "等待对方" : "再战一局";
  $("resign").hidden = seat < 0 || !["FIGHTING", "PAUSED"].includes(state?.phase);
  $("resign").disabled = !usable;
  $("leave").hidden = seat < 0;
  $("leave").disabled = !usable;
  const canAct = usable && state?.phase === "FIGHTING" && state.activeSeat === seat;
  for (const button of document.querySelectorAll("[data-action]")) {
    const skill = me?.skills.find((skill) => skill.action === button.dataset.action);
    button.disabled = !canAct || !skill?.available;
    button.title = skill?.reason || (!canAct ? "等待你的行动回合" : button.querySelector("strong").textContent);
  }
  $("drain-meta").textContent = me?.cooldown ? `冷却 ${me.cooldown} 个行动回合` : "120% · 汲取 50%";
  $("potion-meta").textContent = me ? `恢复 50 HP · 剩 ${me.potions}` : "恢复 50 HP";
  $("action-status").textContent = !connected ? "等待连接恢复" : pending ? "操作确认中" : seat < 0 ? "观战中" : state?.phase === "FIGHTING" ? (state.activeSeat === seat ? "轮到你行动" : "等待对手行动") : phaseText();
}
function renderState(next, force = false) {
  if (!force && state && next.revision < state.revision) return;
  const previous = state;
  state = next;
  clockOffset = next.serverTime - Date.now();
  $("duel-scene").dataset.revision = String(state.revision);
  $("duel-scene").dataset.matchId = state.matchId;
  $("duel-scene").dataset.phase = state.phase;
  for (let index = 0; index < 2; index++) {
    const player = fighter(index), element = $(`fighter-${index}`);
    element.querySelector(".fighter-name").textContent = player?.name || "等待加入";
    element.querySelector(".fighter-presence").textContent = !player ? "空席" : !player.connected ? "离线" : state.phase === "WAITING" ? player.ready ? "已准备" : "未准备" : index === seat ? "你" : "在线";
    element.querySelector(".fighter-hp").textContent = player ? `${player.hp} / ${player.maxHp}` : "-- / --";
    element.querySelector(".hp-fill").style.width = `${player ? player.hp / player.maxHp * 100 : 0}%`;
    element.querySelector(".hp-track").setAttribute("aria-valuenow", String(player?.hp || 0));
    element.querySelector(".combat-stats").textContent = player ? `ATK ${player.attack} · DEF ${player.defense}` : "ATK -- · DEF --";
    element.querySelector(".guard-status").textContent = player?.guarding ? "防御姿态" : "";
  }
  $("phase-label").textContent = phaseText();
  $("turn-number").textContent = `回合 ${String(state.turn).padStart(2, "0")}`;
  $("arena-outcome").textContent = state.phase === "FINISHED" ? state.reason : "PUBLIC ARENA / 01";
  renderLog(previous?.matchId !== state.matchId);
  renderControls();
  document.dispatchEvent(new CustomEvent("duel-state", { detail: { state, previous } }));
}
function renderLog(reset) {
  const list = $("duel-log");
  const follow = list.scrollHeight - list.scrollTop - list.clientHeight < 48;
  if (reset) list.replaceChildren();
  const ids = new Set(state.log.map((entry) => String(entry.id)));
  for (const child of [...list.children]) if (!ids.has(child.dataset.id)) child.remove();
  const existing = new Set([...list.children].map((child) => child.dataset.id));
  for (const entry of state.log) {
    if (existing.has(String(entry.id))) continue;
    const row = document.createElement("li"), time = document.createElement("time"), text = document.createElement("p");
    row.dataset.id = String(entry.id);
    time.textContent = `回合 ${String(entry.turn).padStart(2, "0")}`;
    text.textContent = entry.text;
    row.append(time, text);
    list.append(row);
  }
  $("log-total").textContent = String(state.log.length);
  $("empty-log").hidden = state.log.length > 0;
  if (follow || reset) list.scrollTop = list.scrollHeight;
}
function connectionState(kind, label) {
  $("duel-status").dataset.state = kind;
  $("connection-label").textContent = label;
  $("reconnect").hidden = connected || (!halted && socket !== null);
  renderControls();
}
function send(message) {
  if (socket?.readyState !== WebSocket.OPEN) return false;
  try { socket.send(JSON.stringify({ v: 1, ...message })); return true; }
  catch { socket.close(); return false; }
}
function command(type, extra = {}) {
  if (!connected || pending) return;
  error();
  const opId = `${Date.now().toString(36)}_${++counter}`;
  if (send({ type, opId, matchId: state.matchId, ...extra })) {
    pending = opId;
    commandTimer = setTimeout(() => { if (pending === opId) { error("操作确认超时，正在重新连接。" ); socket?.close(); } }, 8000);
    renderControls();
  }
}
function finishPending(opId) {
  if (pending === opId) { pending = null; clearTimeout(commandTimer); renderControls(); }
}
function connect() {
  clearTimeout(retryTimer);
  halted = false;
  connected = false;
  const current = new WebSocket(`${location.protocol === "https:" ? "wss:" : "ws:"}//${location.host}/ws/duel`);
  socket = current;
  connectionState("connecting", "连接中");
  connectionTimer = setTimeout(() => { if (socket === current && !connected) current.close(); }, 8000);
  current.addEventListener("open", () => {
    if (socket !== current) return;
    send({ type: "hello", resumeToken });
    lastReceived = Date.now();
    heartbeatTimer = setInterval(() => { if (Date.now() - lastReceived > 65000) current.close(); else send({ type: "ping" }); }, 25000);
  });
  current.addEventListener("message", (message) => {
    if (socket !== current) return;
    lastReceived = Date.now();
    try {
      const event = JSON.parse(message.data);
      if (event.v !== 1) throw new Error("Unsupported protocol");
      if (event.type === "welcome") {
        seat = event.yourSeat;
        remember(event.resumeToken);
        connected = true;
        retryDelay = 1000;
        clearTimeout(connectionTimer);
        error(event.resumeError ? errorMessages[event.resumeError] || "席位恢复失败。" : "");
        renderState(event.state, true);
        connectionState("connected", "已连接");
      } else if (event.type === "state") renderState(event.state);
      else if (event.type === "ack") finishPending(event.opId);
      else if (event.type === "error") {
        renderState(event.state);
        finishPending(event.opId);
        error(errorMessages[event.code] || "操作未被接受，请重试。" );
      }
    } catch { error("收到异常数据，正在重新连接。" ); current.close(); }
  });
  current.addEventListener("close", (event) => {
    if (socket !== current) return;
    socket = null;
    connected = false;
    pending = null;
    clearTimeout(connectionTimer); clearInterval(heartbeatTimer); clearTimeout(commandTimer);
    if (event.code === 4001) { halted = true; seat = -1; remember(""); error("席位已在另一个页面恢复，此页已断开。" ); }
    connectionState("idle", halted ? "已断开" : "重连中");
    if (!halted) { retryTimer = setTimeout(connect, retryDelay); retryDelay = Math.min(8000, retryDelay * 2); }
  });
}
$("join-form").addEventListener("submit", (event) => { event.preventDefault(); command("join", { name: $("nickname").value.trim() }); });
$("ready").addEventListener("click", () => command("ready"));
$("rematch").addEventListener("click", () => command("rematch"));
for (const button of document.querySelectorAll("[data-action]")) button.addEventListener("click", () => command("action", { action: button.dataset.action, turn: state.turn }));
function confirm(type) {
  confirmType = type;
  confirmMatch = state?.matchId;
  const active = ["FIGHTING", "PAUSED"].includes(state?.phase);
  $("confirm-title").textContent = type === "resign" ? "确认认输？" : "离开当前席位？";
  $("confirm-description").textContent = type === "resign" || active ? "本局将判定对手获胜。" : "席位将立即释放。";
  $("confirm-dialog").returnValue = "cancel";
  $("confirm-dialog").showModal();
}
$("resign").addEventListener("click", () => confirm("resign"));
$("leave").addEventListener("click", () => confirm("leave"));
$("confirm-dialog").addEventListener("close", () => {
  if ($("confirm-dialog").returnValue !== "confirm") return;
  if (state?.matchId !== confirmMatch) { error("对局已更新，请重新确认。" ); return; }
  command(confirmType);
});
$("reconnect").addEventListener("click", () => { if (!socket) { error(); connect(); } });
setInterval(() => { if (state?.phase === "PAUSED") { $("phase-label").textContent = phaseText(); renderControls(); } }, 500);
window.addEventListener("pagehide", () => {
  halted = true;
  clearTimeout(retryTimer); clearTimeout(connectionTimer); clearTimeout(commandTimer); clearInterval(heartbeatTimer);
  const previous = socket; socket = null; connected = false; pending = null;
  previous?.close(1000, "Page hidden");
});
window.addEventListener("pageshow", (event) => { if (event.persisted) connect(); });
connect();
