// 生成应用图标（纯 Node，无第三方依赖）：启动图标 + 通知小图标
const zlib = require('zlib');
const fs = require('fs');
const path = require('path');

function crc32(buf) {
  let table = crc32.table;
  if (!table) {
    table = crc32.table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let c = n;
      for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
      table[n] = c;
    }
  }
  let crc = -1;
  for (let i = 0; i < buf.length; i++) crc = (crc >>> 8) ^ table[(crc ^ buf[i]) & 0xff];
  return (crc ^ -1) >>> 0;
}

function pngEncode(width, height, rgba) {
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    const row = y * (width * 4 + 1);
    raw[row] = 0;
    rgba.copy(raw, row + 1, y * width * 4, (y + 1) * width * 4);
  }
  const idat = zlib.deflateSync(raw, { level: 9 });
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
    const crcBuf = Buffer.alloc(4);
    crcBuf.writeUInt32BE(crc32(body));
    return Buffer.concat([len, body, crcBuf]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', idat),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// 距线段 AB 的距离
function segDist(px, py, ax, ay, bx, by) {
  const dx = bx - ax, dy = by - ay;
  const l2 = dx * dx + dy * dy;
  let t = l2 === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / l2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(px - (ax + t * dx), py - (ay + t * dy));
}

// 时钟几何：外圈 + 10:10 指针 + 中心点；rings = [{r, w}], hands = [{angleDeg, len, w}]
function clockShape(nx, ny, cx, cy, rings, hands, dotR) {
  const dx = nx - cx, dy = ny - cy;
  const r = Math.hypot(dx, dy);
  let hit = false;
  for (const ring of rings) {
    if (Math.abs(r - ring.r) < ring.w) { hit = true; break; }
  }
  if (!hit) {
    for (const h of hands) {
      const rad = (h.angleDeg * Math.PI) / 180;
      // 12 点方向为 0°，顺时针
      const ex = cx + Math.sin(rad) * h.len;
      const ey = cy - Math.cos(rad) * h.len;
      if (segDist(nx, ny, cx, cy, ex, ey) < h.w) { hit = true; break; }
    }
  }
  if (!hit && r < dotR) hit = true;
  return hit;
}

function renderLauncher(size) {
  const ss = 3, S = size * ss;
  const out = Buffer.alloc(size * size * 4);
  const top = [20, 184, 166], bot = [15, 118, 110]; // teal 渐变
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let r = 0, g = 0, b = 0, aSum = 0;
      for (let sy = 0; sy < ss; sy++) {
        for (let sx = 0; sx < ss; sx++) {
          const nx = (x * ss + sx + 0.5) / S, ny = (y * ss + sy + 0.5) / S;
          // 圆角矩形背景
          const rr = 0.22, qx = Math.abs(nx - 0.5) - (0.5 - rr), qy = Math.abs(ny - 0.5) - (0.5 - rr);
          const d = Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0) - rr;
          if (d >= 0) continue;
          let cr, cg, cb;
          if (clockShape(nx, ny, 0.5, 0.47,
            [{ r: 0.265, w: 0.045 }],
            [{ angleDeg: 300, len: 0.13, w: 0.024 }, { angleDeg: 60, len: 0.185, w: 0.024 }],
            0.035)) {
            cr = cg = cb = 255;
          } else {
            const t = ny;
            cr = top[0] + (bot[0] - top[0]) * t;
            cg = top[1] + (bot[1] - top[1]) * t;
            cb = top[2] + (bot[2] - top[2]) * t;
          }
          r += cr; g += cg; b += cb; aSum++;
        }
      }
      const i = (y * size + x) * 4;
      if (aSum > 0) {
        out[i] = Math.round(r / aSum);
        out[i + 1] = Math.round(g / aSum);
        out[i + 2] = Math.round(b / aSum);
        out[i + 3] = Math.round((aSum / (ss * ss)) * 255);
      }
    }
  }
  return out;
}

function renderStat(size) {
  const ss = 3, S = size * ss;
  const out = Buffer.alloc(size * size * 4);
  for (let y = 0; y < size; y++) {
    for (let x = 0; x < size; x++) {
      let aSum = 0;
      for (let sy = 0; sy < ss; sy++) {
        for (let sx = 0; sx < ss; sx++) {
          const nx = (x * ss + sx + 0.5) / S, ny = (y * ss + sy + 0.5) / S;
          if (clockShape(nx, ny, 0.5, 0.5,
            [{ r: 0.38, w: 0.085 }],
            [{ angleDeg: 300, len: 0.19, w: 0.05 }, { angleDeg: 60, len: 0.27, w: 0.05 }],
            0.06)) aSum++;
        }
      }
      const i = (y * size + x) * 4;
      if (aSum > 0) {
        out[i] = out[i + 1] = out[i + 2] = 255;
        out[i + 3] = Math.round((aSum / (ss * ss)) * 255);
      }
    }
  }
  return out;
}

const res = path.join(__dirname, '..', 'res');
const launcherSizes = { mdpi: 48, hdpi: 72, xhdpi: 96, xxhdpi: 144, xxxhdpi: 192 };
for (const [dpi, s] of Object.entries(launcherSizes)) {
  const dir = path.join(res, 'mipmap-' + dpi);
  fs.mkdirSync(dir, { recursive: true });
  fs.writeFileSync(path.join(dir, 'ic_launcher.png'), pngEncode(s, s, renderLauncher(s)));
  console.log('ic_launcher', dpi, s + 'px done');
}
const nodpi = path.join(res, 'drawable-nodpi');
fs.mkdirSync(nodpi, { recursive: true });
fs.writeFileSync(path.join(nodpi, 'ic_stat.png'), pngEncode(96, 96, renderStat(96)));
console.log('ic_stat 96px done');
