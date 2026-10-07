// Minimal, strict zip extractor for uploaded games.
// Only regular files (stored or deflated) are written; symlinks, absolute
// paths and ".." segments are rejected, and total output size is capped.
const fs = require('node:fs');
const path = require('node:path');
const zlib = require('node:zlib');

const EOCD_SIG = 0x06054b50;
const CEN_SIG = 0x02014b50;
const LOC_SIG = 0x04034b50;

function findEndOfCentralDir(buf) {
  const min = Math.max(0, buf.length - 0xffff - 22);
  for (let i = buf.length - 22; i >= min; i--) {
    if (buf.readUInt32LE(i) === EOCD_SIG) return i;
  }
  throw new Error('Not a valid zip file');
}

function safeRelative(name) {
  const clean = name.replace(/\\/g, '/');
  if (clean.startsWith('/') || /^[a-zA-Z]:/.test(clean)) return null;
  const parts = clean.split('/').filter((p) => p && p !== '.');
  if (parts.some((p) => p === '..')) return null;
  return parts.join('/');
}

function listEntries(buf) {
  const eocd = findEndOfCentralDir(buf);
  const count = buf.readUInt16LE(eocd + 10);
  let off = buf.readUInt32LE(eocd + 16);
  const entries = [];
  const seen = new Set();
  for (let i = 0; i < count; i++) {
    if (off + 46 > buf.length || buf.readUInt32LE(off) !== CEN_SIG) {
      throw new Error('Corrupt zip directory');
    }
    const flags = buf.readUInt16LE(off + 8);
    const method = buf.readUInt16LE(off + 10);
    const compSize = buf.readUInt32LE(off + 20);
    const size = buf.readUInt32LE(off + 24);
    const nameLen = buf.readUInt16LE(off + 28);
    const extraLen = buf.readUInt16LE(off + 30);
    const commentLen = buf.readUInt16LE(off + 32);
    const extAttr = buf.readUInt32LE(off + 38);
    const localOff = buf.readUInt32LE(off + 42);
    const name = buf.toString('utf8', off + 46, off + 46 + nameLen);
    off += 46 + nameLen + extraLen + commentLen;

    if (name.endsWith('/')) continue; // directory
    const unixMode = (extAttr >>> 16) & 0o170000;
    if (unixMode === 0o120000) continue; // symlink: skip
    if (flags & 0x1) throw new Error('Encrypted zips are not supported');
    const rel = safeRelative(name);
    if (!rel) throw new Error(`Unsafe path in zip: ${name}`);
    if (rel.startsWith('__MACOSX/')) continue;
    if (seen.has(rel.toLowerCase())) throw new Error(`Duplicate file in zip: ${rel}`);
    seen.add(rel.toLowerCase());
    entries.push({ rel, method, compSize, size, localOff });
  }
  return entries;
}

function readEntry(buf, e, remaining) {
  const lo = e.localOff;
  if (lo + 30 > buf.length || buf.readUInt32LE(lo) !== LOC_SIG) {
    throw new Error('Corrupt zip entry');
  }
  const start = lo + 30 + buf.readUInt16LE(lo + 26) + buf.readUInt16LE(lo + 28);
  const end = start + e.compSize;
  if (end > buf.length) throw new Error('Corrupt zip entry');
  const raw = buf.subarray(start, end);
  if (e.method === 0) {
    if (raw.length > remaining) throw new Error('Game is too large once unzipped');
    return raw;
  }
  if (e.method === 8) {
    try {
      return zlib.inflateRawSync(raw, { maxOutputLength: Math.max(1, remaining) });
    } catch (err) {
      if (err.code === 'ERR_BUFFER_TOO_LARGE' || err instanceof RangeError) {
        throw new Error('Game is too large once unzipped');
      }
      throw new Error('Corrupt zip data');
    }
  }
  throw new Error('Unsupported zip compression method');
}

/**
 * Extract `buf` into `destDir`. Returns the list of written relative paths.
 */
function extractZip(buf, destDir, { maxTotalBytes, maxFiles = 5000 }) {
  const entries = listEntries(buf);
  if (entries.length === 0) throw new Error('The zip file is empty');
  if (entries.length > maxFiles) throw new Error('Too many files in zip');
  let remaining = maxTotalBytes;
  const root = path.resolve(destDir);
  const written = [];
  for (const e of entries) {
    const data = readEntry(buf, e, remaining);
    remaining -= data.length;
    if (remaining < 0) throw new Error('Game is too large once unzipped');
    const target = path.resolve(root, e.rel);
    if (!target.startsWith(root + path.sep)) throw new Error('Unsafe path in zip');
    fs.mkdirSync(path.dirname(target), { recursive: true });
    fs.writeFileSync(target, data, { mode: 0o644, flag: 'wx' });
    written.push(e.rel);
  }
  return written;
}

module.exports = { extractZip, safeRelative };
