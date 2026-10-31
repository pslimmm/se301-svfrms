// Authoring-only SVG rasterization; Java runtime/build do not depend on Node or sharp.
import fs from 'node:fs/promises';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const require = createRequire(import.meta.url);
const sharp = require(process.env.CODEX_NODE_MODULES ? path.join(process.env.CODEX_NODE_MODULES, 'sharp') : 'sharp');
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
for (const name of ['component', 'sequence']) {
  const dir=path.join(root,'docs','diagrams');
  await sharp(await fs.readFile(path.join(dir,name+'.svg'))).resize(1800).png().toFile(path.join(dir,name+'.png'));
}
