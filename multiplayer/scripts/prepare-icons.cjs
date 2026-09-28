const { copyFileSync, mkdirSync } = require("node:fs");
const { resolve } = require("node:path");
const root = resolve(__dirname, "..");
const source = resolve(root, "node_modules/lucide-static");
const target = resolve(root, "src/main/resources/static/icons");
mkdirSync(target, { recursive: true });
for (const icon of ["radio", "plug", "trash-2", "messages-square", "send", "pencil", "eraser", "download", "users", "swords", "sword", "shield", "heart-pulse", "flask-conical", "zap", "flag", "log-out", "rotate-ccw", "circle-check"]) {
  copyFileSync(resolve(source, "icons", `${icon}.svg`), resolve(target, `${icon}.svg`));
}
copyFileSync(resolve(source, "LICENSE"), resolve(target, "LICENSE.txt"));
