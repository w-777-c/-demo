const fs = require('node:fs');
const path = require('node:path');
const { Resvg } = require('../build/ui-tools/node_modules/@resvg/resvg-js');
const root = path.join(__dirname, '..');
const source = path.join(root, 'multiplayer/node_modules/lucide-static/icons');
const output = path.join(root, 'fightinggame/resources/icons');
fs.mkdirSync(output, { recursive: true });
for (const name of ['sword', 'swords', 'shield', 'heart-pulse', 'flask-conical', 'zap', 'settings-2', 'volume-2', 'volume-x', 'x', 'minus', 'maximize-2', 'trophy', 'users', 'sparkles', 'chevron-right', 'crown', 'music-2', 'gem', 'log-out']) {
  const svg = fs.readFileSync(path.join(source, name + '.svg'), 'utf8').replace(/currentColor/g, '#e9d6ac');
  fs.writeFileSync(path.join(output, name + '.png'), new Resvg(svg, { fitTo: { mode: 'width', value: 64 } }).render().asPng());
}
fs.copyFileSync(path.join(root, 'multiplayer/src/main/resources/static/icons/LICENSE.txt'), path.join(output, 'LICENSE.txt'));
console.log('Prepared 20 local Lucide icons.');
const crown = fs.readFileSync(path.join(source, 'crown.svg'), 'utf8').replace(/currentColor/g, '#e9d6ac');
const appSvg = crown.replace('viewBox="0 0 24 24"', 'viewBox="-5 -5 34 34"').replace(/(<svg[^>]*>)/, '$1<rect x="-5" y="-5" width="34" height="34" rx="5" fill="#531d30" stroke="none"/>');
const png = new Resvg(appSvg, { fitTo: { mode: 'width', value: 256 } }).render().asPng();
const header = Buffer.alloc(22);
header.writeUInt16LE(1, 2); header.writeUInt16LE(1, 4);
// ICO directory entry: zero width/height means 256px, followed by a 32-bit PNG.
header.writeUInt8(0, 6); header.writeUInt8(0, 7); header.writeUInt8(0, 8); header.writeUInt8(0, 9);
header.writeUInt16LE(1, 10); header.writeUInt16LE(32, 12);
header.writeUInt32LE(png.length, 14); header.writeUInt32LE(22, 18);
fs.writeFileSync(path.join(root, 'packaging/IronArena.ico'), Buffer.concat([header, png]));
console.log('Prepared Windows application icon.');
