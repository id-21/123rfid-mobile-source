#!/usr/bin/env node
// Zero-dependency mock order server for testing the Android app over Wi-Fi.
//   node server.js                 -> http://0.0.0.0:8080
//   PORT=9000 TOKEN=secret node server.js
// State lives in memory and resets when you restart it.
// See docs/API_CONTRACT.md for the endpoints the app uses.

const http = require('http');
const os = require('os');

const PORT = parseInt(process.env.PORT || '8080', 10);
const TOKEN = process.env.TOKEN || '';

const epc = (sku, roll) =>
  'E28011' + sku.toString(16).toUpperCase().padStart(2, '0') +
  roll.toString(16).toUpperCase().padStart(16, '0');

const line = (sku, description, skuIndex, firstRoll, qty) => ({
  sku, description, qty,
  epcs: Array.from({ length: qty }, (_, i) => epc(skuIndex, firstRoll + i)),
});

const now = () => new Date().toISOString().replace(/\.\d+Z$/, 'Z');

const orders = [
  { id: '2001', customer: 'Server Sample Studio, Kolkata', status: 'new', claimedBy: null, updatedAt: now(),
    lines: [line('CM-2201', 'Cristiana Masi - sample A', 1, 101, 3), line('OP-1105', 'OH POPSI - sample B', 2, 101, 2)] },
  { id: '2002', customer: 'Server Sample Interiors, Mumbai', status: 'new', claimedBy: null, updatedAt: now(),
    lines: [line('CO-3310', 'Coordonne - sample C', 3, 101, 4)] },
];
const completions = []; // every accepted completion

function send(res, code, body) {
  const data = JSON.stringify(body);
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(data) });
  res.end(data);
}

function readJson(req) {
  return new Promise((resolve, reject) => {
    let raw = '';
    req.on('data', (c) => { raw += c; if (raw.length > 1e6) req.destroy(); });
    req.on('end', () => { try { resolve(raw ? JSON.parse(raw) : {}); } catch (e) { reject(e); } });
    req.on('error', reject);
  });
}

function addSampleOrder() {
  const next = Math.max(2000, ...orders.map((o) => parseInt(o.id, 10) || 0)) + 1;
  const skuIndex = 10 + (next % 5);
  const o = { id: String(next), customer: 'Server Sample Customer ' + next, status: 'new', claimedBy: null, updatedAt: now(),
    lines: [line('SRV-' + skuIndex, 'Server test roll', skuIndex, next * 10, 2)] };
  orders.push(o);
  return o;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const path = url.pathname.replace(/\/+$/, '') || '/';
  console.log(new Date().toISOString(), req.method, req.url);

  if (path === '/') {
    return send(res, 200, { service: 'mock order server', open: orders.filter((o) => o.status !== 'completed').length,
      hint: 'POST /admin/orders adds a sample order; GET /admin/completions shows what the app submitted' });
  }
  if (TOKEN && req.headers.authorization !== 'Bearer ' + TOKEN) {
    return send(res, 401, { error: 'bad or missing token' });
  }

  try {
    if (req.method === 'GET' && path === '/orders') {
      const wanted = (url.searchParams.get('status') || 'new,in_progress').split(',');
      return send(res, 200, { orders: orders.filter((o) => wanted.includes(o.status)) });
    }

    let m = path.match(/^\/orders\/([^/]+)\/claim$/);
    if (req.method === 'POST' && m) {
      const o = orders.find((x) => x.id === decodeURIComponent(m[1]));
      if (!o) return send(res, 404, { error: 'no such order' });
      const { device } = await readJson(req);
      if (!device) return send(res, 400, { error: 'device required' });
      if (o.status === 'new') {
        Object.assign(o, { status: 'in_progress', claimedBy: device, updatedAt: now() });
        return send(res, 200, o);
      }
      if (o.status === 'in_progress' && o.claimedBy === device) return send(res, 200, o);
      return send(res, 409, { error: 'order already taken', claimedBy: o.claimedBy });
    }

    m = path.match(/^\/orders\/([^/]+)\/complete$/);
    if (req.method === 'POST' && m) {
      const o = orders.find((x) => x.id === decodeURIComponent(m[1]));
      if (!o) return send(res, 404, { error: 'no such order' });
      const c = await readJson(req);
      if (!c.completionId) return send(res, 400, { error: 'completionId required' });
      const prior = completions.find((x) => x.completionId === c.completionId);
      if (prior) return send(res, 200, { ok: true, duplicate: true }); // idempotent retry
      if (o.status === 'completed') return send(res, 409, { error: 'order already completed' });
      completions.push(c);
      Object.assign(o, { status: 'completed', updatedAt: now() });
      return send(res, 200, { ok: true });
    }

    if (req.method === 'POST' && path === '/admin/orders') return send(res, 201, addSampleOrder());
    if (req.method === 'GET' && path === '/admin/completions') return send(res, 200, { completions });
    if (req.method === 'GET' && path === '/admin/orders') return send(res, 200, { orders });

    return send(res, 404, { error: 'not found' });
  } catch (e) {
    return send(res, 400, { error: String(e.message || e) });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('Mock order server on port ' + PORT + (TOKEN ? ' (token required)' : ' (no token)'));
  for (const addrs of Object.values(os.networkInterfaces())) {
    for (const a of addrs || []) {
      if (a.family === 'IPv4' && !a.internal) console.log('  phone URL:  http://' + a.address + ':' + PORT);
    }
  }
  console.log('Add a sample order:  curl -X POST http://localhost:' + PORT + '/admin/orders');
});
