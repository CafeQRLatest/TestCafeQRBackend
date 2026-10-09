/**
 * server.js — Cafe QR Multi-Tenant WhatsApp Companion Gateway
 * 
 * Provides an isolated, zero-cost WhatsApp Web integration using Baileys.
 * Supports multi-session architecture:
 * - Each Branch/Organization has its own independent session: `org_<orgId>`
 * - Optional Client-level master fallback session: `client_<clientId>`
 * - Sessions are persisted in `auth_sessions/<sessionId>/`
 */

const express = require('express');
const cors = require('cors');
const qrcode = require('qrcode');
const pino = require('pino');
const path = require('path');
const fs = require('fs');

const {
  default: makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
  fetchLatestBaileysVersion,
  makeCacheableSignalKeyStore,
  Browsers
} = require('@whiskeysockets/baileys');

const PORT = process.env.PORT || 3005;
const AUTH_DIR = path.join(__dirname, 'auth_sessions');

// Ensure base sessions directory exists
if (!fs.existsSync(AUTH_DIR)) {
  fs.mkdirSync(AUTH_DIR, { recursive: true });
}

const app = express();
app.use(cors());
app.use(express.json({ limit: '15mb' }));

const logger = pino({ level: 'info' });

// In-memory registry of active branch/client sessions
const sessions = new Map();

/**
 * Normalizes input sessionId into a safe, valid filesystem directory name.
 */
function normalizeSessionId(raw) {
  if (!raw || typeof raw !== 'string') return 'default';
  const clean = raw.trim().replace(/[^a-zA-Z0-9_-]/g, '_');
  return clean || 'default';
}

/**
 * Retrieves an existing session or initializes a new one.
 */
async function getOrCreateSession(rawSessionId) {
  const sessionId = normalizeSessionId(rawSessionId);

  if (sessions.has(sessionId)) {
    return sessions.get(sessionId);
  }

  const sessionObj = {
    id: sessionId,
    sock: null,
    currentQr: null,
    qrDataUrl: null,
    connectionState: 'DISCONNECTED', // 'DISCONNECTED' | 'SCAN_QR' | 'CONNECTING' | 'CONNECTED'
    connectedUser: null,
    reconnectTimer: null
  };

  sessions.set(sessionId, sessionObj);
  await initSession(sessionObj);
  return sessionObj;
}

/**
 * Initializes a Baileys socket for a specific branch/client session.
 */
async function initSession(sessionObj) {
  const sessionId = sessionObj.id;
  const sessionDir = path.join(AUTH_DIR, sessionId);

  if (!fs.existsSync(sessionDir)) {
    fs.mkdirSync(sessionDir, { recursive: true });
  }

  if (sessionObj.reconnectTimer) {
    clearTimeout(sessionObj.reconnectTimer);
    sessionObj.reconnectTimer = null;
  }

  sessionObj.connectionState = 'CONNECTING';
  logger.info(`[WhatsApp Gateway] Initializing session [${sessionId}]...`);

  try {
    const { state, saveCreds } = await useMultiFileAuthState(sessionDir);

    // Fetch version safely with a 2-second timeout to prevent hanging if GitHub raw is slow/unreachable
    let version = [2, 3000, 1015901307];
    try {
      const v = await Promise.race([
        fetchLatestBaileysVersion(),
        new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 2500))
      ]);
      if (v && v.version) {
        version = v.version;
      }
    } catch (verErr) {
      logger.warn(`[WhatsApp Gateway] Using fallback Baileys version: ${verErr.message}`);
    }

    const sock = makeWASocket({
      version,
      auth: {
        creds: state.creds,
        keys: makeCacheableSignalKeyStore(state.keys, logger)
      },
      printQRInTerminal: false,
      logger: pino({ level: 'info' }),
      browser: Browsers.ubuntu('Chrome'),
      connectTimeoutMs: 60000,
      defaultQueryTimeoutMs: 60000,
      syncFullHistory: false
    });

    sessionObj.sock = sock;

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
      const { connection, lastDisconnect, qr } = update;

      if (qr) {
        sessionObj.currentQr = qr;
        sessionObj.connectionState = 'SCAN_QR';
        try {
          sessionObj.qrDataUrl = await qrcode.toDataURL(qr, {
            margin: 2,
            width: 320,
            color: { dark: '#0f172a', light: '#ffffff' }
          });
          logger.info(`[WhatsApp Gateway] [${sessionId}] New QR code generated successfully (${sessionObj.qrDataUrl?.length || 0} chars). Waiting for scan...`);
        } catch (e) {
          logger.error({ err: e, sessionId }, 'Failed to convert QR to Data URL');
        }
      }

      if (connection === 'close') {
        sessionObj.currentQr = null;
        sessionObj.qrDataUrl = null;
        sessionObj.connectedUser = null;

        const statusCode = lastDisconnect?.error?.output?.statusCode;
        const shouldReconnect = statusCode !== DisconnectReason.loggedOut;
        sessionObj.connectionState = 'DISCONNECTED';

        logger.warn(`[WhatsApp Gateway] [${sessionId}] Connection closed. Code: ${statusCode}. Reconnect: ${shouldReconnect}`);

        if (statusCode === DisconnectReason.loggedOut) {
          logger.info(`[WhatsApp Gateway] [${sessionId}] Session logged out. Clearing auth files.`);
          try {
            fs.rmSync(sessionDir, { recursive: true, force: true });
            fs.mkdirSync(sessionDir, { recursive: true });
          } catch (err) {
            logger.error({ err, sessionId }, 'Error cleaning session directory');
          }
          // Restart clean for new scan
          initSession(sessionObj);
        } else if (shouldReconnect) {
          sessionObj.reconnectTimer = setTimeout(() => {
            initSession(sessionObj);
          }, 4000);
        }
      } else if (connection === 'open') {
        sessionObj.currentQr = null;
        sessionObj.qrDataUrl = null;
        sessionObj.connectionState = 'CONNECTED';
        const user = sock.user;
        const rawPhone = (user?.id || '').split(':')[0].split('@')[0];
        sessionObj.connectedUser = {
          id: user?.id,
          phone: rawPhone,
          name: user?.name || user?.notify || 'Cafe QR Branch'
        };
        logger.info(`[WhatsApp Gateway] [${sessionId}] Connected successfully! Phone: +${rawPhone}`);
      }
    });

  } catch (error) {
    logger.error({ err: error, sessionId }, `[WhatsApp Gateway] Session [${sessionId}] initialization failed`);
    sessionObj.connectionState = 'DISCONNECTED';
    sessionObj.reconnectTimer = setTimeout(() => initSession(sessionObj), 5000);
  }
}

/**
 * Format phone number to WhatsApp JID (e.g. 919847920009@s.whatsapp.net)
 */
function formatToJid(inputPhone) {
  if (!inputPhone) return null;
  let digits = String(inputPhone).replace(/[^0-9]/g, '');
  if (!digits) return null;

  // If 10 digits (Standard Indian mobile number without country code), prepend 91
  if (digits.length === 10) {
    digits = '91' + digits;
  }
  return `${digits}@s.whatsapp.net`;
}

// ─────────────────────────────────────────────────────────────────────────────
// REST API ENDPOINTS
// ─────────────────────────────────────────────────────────────────────────────

// 1. Connection status (accepts query param ?sessionId=...)
app.get('/api/status', async (req, res) => {
  const session = await getOrCreateSession(req.query.sessionId);
  res.json({
    success: true,
    sessionId: session.id,
    status: session.connectionState,
    hasQr: !!session.qrDataUrl,
    user: session.connectedUser
  });
});

// 2. Fetch QR code (accepts query param ?sessionId=...)
app.get('/api/qr', async (req, res) => {
  const session = await getOrCreateSession(req.query.sessionId);
  if (session.connectionState === 'CONNECTED') {
    return res.json({
      success: true,
      sessionId: session.id,
      status: 'CONNECTED',
      message: 'Already connected',
      user: session.connectedUser
    });
  }
  res.json({
    success: true,
    sessionId: session.id,
    status: session.connectionState,
    qr: session.qrDataUrl
  });
});

// 3. Send digital bill (with branch session and optional fallback session)
app.post('/api/send-bill', async (req, res) => {
  const { sessionId, fallbackSessionId, phone, text, pdfBase64, filename } = req.body;

  let activeSession = null;
  const primaryId = normalizeSessionId(sessionId);
  const primary = sessions.get(primaryId);

  if (primary && primary.connectionState === 'CONNECTED' && primary.sock) {
    activeSession = primary;
  } else if (fallbackSessionId) {
    const fallbackId = normalizeSessionId(fallbackSessionId);
    const fallback = sessions.get(fallbackId);
    if (fallback && fallback.connectionState === 'CONNECTED' && fallback.sock) {
      activeSession = fallback;
      logger.info(`[WhatsApp Gateway] Primary [${primaryId}] not connected. Using fallback session [${fallbackId}]`);
    }
  }

  // If still not found in active memory, attempt to load primary
  if (!activeSession) {
    const loaded = await getOrCreateSession(sessionId);
    if (loaded && loaded.connectionState === 'CONNECTED' && loaded.sock) {
      activeSession = loaded;
    }
  }

  if (!activeSession || activeSession.connectionState !== 'CONNECTED' || !activeSession.sock) {
    return res.status(503).json({
      success: false,
      message: `WhatsApp is not linked for branch [${primaryId}]. Scan QR in Configurations -> Customers to link.`
    });
  }

  const jid = formatToJid(phone);
  if (!jid) {
    return res.status(400).json({
      success: false,
      message: 'Invalid customer phone number'
    });
  }

  if (!text && !pdfBase64) {
    return res.status(400).json({
      success: false,
      message: 'Message text or PDF document is required'
    });
  }

  try {
    let sentMsg = null;

    // Send formatted digital receipt
    if (text) {
      sentMsg = await activeSession.sock.sendMessage(jid, { text: String(text).trim() });
      logger.info(`[WhatsApp Gateway] [${activeSession.id}] Sent digital bill to ${jid}`);
    }

    // Optionally send PDF invoice attachment
    if (pdfBase64) {
      const buffer = Buffer.from(pdfBase64, 'base64');
      await activeSession.sock.sendMessage(jid, {
        document: buffer,
        mimetype: 'application/pdf',
        fileName: filename || 'Tax_Invoice.pdf',
        caption: '📄 Official Tax Invoice PDF'
      });
      logger.info(`[WhatsApp Gateway] [${activeSession.id}] Sent PDF attachment to ${jid}`);
    }

    return res.json({
      success: true,
      sessionId: activeSession.id,
      messageId: sentMsg?.key?.id,
      timestamp: Date.now()
    });
  } catch (err) {
    logger.error({ err, jid, sessionId: activeSession.id }, '[WhatsApp Gateway] Failed to send message');
    return res.status(500).json({
      success: false,
      message: err.message || 'Failed to dispatch WhatsApp message'
    });
  }
});

// 4. Send test message
app.post('/api/test-message', async (req, res) => {
  const { sessionId, phone } = req.body;
  const session = await getOrCreateSession(sessionId);

  if (session.connectionState !== 'CONNECTED' || !session.sock) {
    return res.status(503).json({
      success: false,
      message: `WhatsApp is not connected for this branch. Please scan the QR code first.`
    });
  }

  const targetPhone = phone || session.connectedUser?.phone;
  if (!targetPhone) {
    return res.status(400).json({ success: false, message: 'Phone number is required' });
  }

  const jid = formatToJid(targetPhone);
  try {
    const branchLabel = session.id.replace('org_', 'Branch ');
    const testText = `✅ *Cafe QR WhatsApp Gateway Test*\n\nYour WhatsApp Digital Bill service is connected and active for *${branchLabel}*! 🚀\n\n_Time: ${new Date().toLocaleString()}_`;
    await session.sock.sendMessage(jid, { text: testText });
    return res.json({ success: true, message: `Test message sent to ${targetPhone}` });
  } catch (err) {
    return res.status(500).json({ success: false, message: err.message });
  }
});

// 5. Disconnect / Unlink session
app.post('/api/disconnect', async (req, res) => {
  const { sessionId } = req.body;
  const cleanId = normalizeSessionId(sessionId);
  const sessionDir = path.join(AUTH_DIR, cleanId);

  try {
    const session = sessions.get(cleanId);
    if (session && session.sock) {
      await session.sock.logout().catch(() => {});
      session.sock.end();
      session.sock = null;
    }

    if (fs.existsSync(sessionDir)) {
      fs.rmSync(sessionDir, { recursive: true, force: true });
      fs.mkdirSync(sessionDir, { recursive: true });
    }

    if (session) {
      session.currentQr = null;
      session.qrDataUrl = null;
      session.connectedUser = null;
      session.connectionState = 'DISCONNECTED';
      setTimeout(() => initSession(session), 1000);
    }

    return res.json({ success: true, message: `Disconnected session [${cleanId}]. You can now scan a new number.` });
  } catch (err) {
    logger.error({ err, sessionId: cleanId }, 'Error during disconnect');
    return res.status(500).json({ success: false, message: err.message });
  }
});

// 6. Health check & Active sessions inventory
app.get('/health', (req, res) => {
  const inventory = [];
  sessions.forEach((s, id) => {
    inventory.push({
      sessionId: id,
      status: s.connectionState,
      phone: s.connectedUser?.phone || null
    });
  });

  res.json({
    status: 'ok',
    totalSessions: sessions.size,
    sessions: inventory,
    uptime: process.uptime()
  });
});

// Auto-restore previously linked sessions on container startup
function autoRestoreExistingSessions() {
  try {
    if (!fs.existsSync(AUTH_DIR)) return;
    const entries = fs.readdirSync(AUTH_DIR, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isDirectory()) {
        const sid = entry.name;
        // Verify creds.json exists in this folder
        if (fs.existsSync(path.join(AUTH_DIR, sid, 'creds.json'))) {
          logger.info(`[WhatsApp Gateway] Auto-restoring existing session: [${sid}]`);
          getOrCreateSession(sid);
        }
      }
    }
  } catch (err) {
    logger.error({ err }, '[WhatsApp Gateway] Error scanning for existing sessions');
  }
}

app.listen(PORT, () => {
  logger.info(`[WhatsApp Gateway] Multi-tenant server running on port ${PORT}`);
  autoRestoreExistingSessions();
});
