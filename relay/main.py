"""
ASTP Relay Server v1.1
Dumb message bus — forwards encrypted blobs, verifies TOTP, stores nothing meaningful.
All session data is ephemeral and purged on expiry.
"""

import asyncio
import base64
import hashlib
import json
import os
import secrets
import time
import uuid
from pathlib import Path
from typing import Optional
from contextlib import asynccontextmanager

import pyotp
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, HTMLResponse, StreamingResponse
from fastapi.staticfiles import StaticFiles
from pydantic import BaseModel

# ---------------------------------------------------------------------------
# Model file — place the .litertlm file at relay/resources/model
# ---------------------------------------------------------------------------

MODEL_FILE_NAME = "gemma4e2b.litertlm"
MODEL_PATH = str(Path(__file__).parent / "resources" / "models" / MODEL_FILE_NAME)


# ---------------------------------------------------------------------------
# Session state
# ---------------------------------------------------------------------------

class AuditEntry:
    def __init__(self, action: str, device_hint: str = ""):
        self.action = action
        self.timestamp = time.time()
        self.device_hint = device_hint

    def to_dict(self):
        return {"action": self.action, "timestamp": int(self.timestamp), "device_hint": self.device_hint}


class Session:
    def __init__(
        self,
        session_id: str,
        totp_seed: str,
        expiry: float,
        patient_eph_public: str,
        patient_eph_signature: str,
        patient_key_fingerprint: str,
    ):
        self.session_id = session_id
        self.totp_seed = totp_seed
        self.expiry = expiry
        self.patient_eph_public = patient_eph_public
        self.patient_eph_signature = patient_eph_signature
        self.patient_key_fingerprint = patient_key_fingerprint
        self.doc_public_key: Optional[str] = None
        self.payload: Optional[dict] = None
        self.audit_log: list[AuditEntry] = []
        # One queue per role — relay pushes events, SSE stream drains them
        self.patient_queue: asyncio.Queue = asyncio.Queue()
        self.doctor_queue: asyncio.Queue = asyncio.Queue()


sessions: dict[str, Session] = {}


# ---------------------------------------------------------------------------
# Lifecycle — background cleanup
# ---------------------------------------------------------------------------

@asynccontextmanager
async def lifespan(app: FastAPI):
    task = asyncio.create_task(_cleanup_loop())
    yield
    task.cancel()


async def _cleanup_loop():
    while True:
        await asyncio.sleep(60)
        now = time.time()
        expired = [sid for sid, s in list(sessions.items()) if s.expiry < now]
        for sid in expired:
            sessions.pop(sid, None)


app = FastAPI(title="ASTP Relay", version="1.1.0", lifespan=lifespan)
app.mount("/static", StaticFiles(directory=Path(__file__).parent / "static"), name="static")


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def get_session(session_id: str) -> Session:
    s = sessions.get(session_id)
    if not s:
        raise HTTPException(404, "Session not found or expired")
    if s.expiry < time.time():
        sessions.pop(session_id, None)
        raise HTTPException(410, "Session expired")
    return s


def verify_totp(seed: str, token: str) -> bool:
    totp = pyotp.TOTP(seed, digest=hashlib.sha256)
    return totp.verify(token, valid_window=1)


def _sse(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data)}\n\n"


# ---------------------------------------------------------------------------
# Request / response models
# ---------------------------------------------------------------------------

class CreateSessionRequest(BaseModel):
    session_id: str               # client-generated UUID v4 — needed so patient can sign (eph_pub || session_id) before calling relay
    patient_eph_public: str       # base64 raw P-256 point (65 bytes)
    patient_eph_signature: str    # base64 DER ECDSA sig over (eph_pub || UTF-8(session_id))
    patient_key_fingerprint: str  # hex SHA-256 of patient long-term pub key raw bytes
    expires_in: int = 300


class JoinRequest(BaseModel):
    doc_public_key: str   # base64 raw P-256 point (65 bytes)
    totp_token: str       # 6-digit TOTP (HMAC-SHA256)


class PayloadRequest(BaseModel):
    ciphertext: str                   # base64 AES-256-GCM ciphertext+tag
    signature: str                    # base64 DER ECDSA sig over (ciphertext || session_id || timestamp_str)
    patient_long_term_public_key: str # base64 raw P-256 point (65 bytes)
    patient_eph_public: str           # base64 raw P-256 point — must match session
    patient_eph_signature: str        # base64 DER ECDSA sig — must match session
    timestamp: int                    # unix seconds


class AuditRequest(BaseModel):
    action: str
    device_hint: str = ""


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------

@app.post("/session/create", status_code=201)
async def create_session(req: CreateSessionRequest):
    """Patient initiates a transfer session with a client-generated session_id."""
    # Validate UUID format to prevent path-traversal or weird input
    try:
        uuid.UUID(req.session_id, version=4)
    except ValueError:
        raise HTTPException(400, "session_id must be a valid UUID v4")

    if req.session_id in sessions:
        raise HTTPException(409, "Session ID already exists — generate a new UUID")

    totp_seed = base64.b32encode(secrets.token_bytes(20)).decode()
    expiry = time.time() + req.expires_in

    sessions[req.session_id] = Session(
        session_id=req.session_id,
        totp_seed=totp_seed,
        expiry=expiry,
        patient_eph_public=req.patient_eph_public,
        patient_eph_signature=req.patient_eph_signature,
        patient_key_fingerprint=req.patient_key_fingerprint,
    )

    return {
        "session_id": req.session_id,
        "totp_seed": totp_seed,
        "expiry": int(expiry),
    }


@app.post("/session/{session_id}/join")
async def doctor_join(session_id: str, req: JoinRequest):
    """Doctor scans QR and joins the session with their ephemeral public key."""
    s = get_session(session_id)

    if s.doc_public_key is not None:
        raise HTTPException(409, "Session already joined by a doctor device")

    if not verify_totp(s.totp_seed, req.totp_token):
        raise HTTPException(403, "Invalid or expired TOTP token")

    s.doc_public_key = req.doc_public_key

    # Notify patient: doctor is here, here's their key → patient derives SAS
    await s.patient_queue.put({
        "event": "doctor_joined",
        "doc_public_key": req.doc_public_key,
    })

    return {"status": "ok"}


@app.get("/session/{session_id}/events")
async def sse_events(session_id: str, role: str = "patient"):
    """
    SSE stream for both patient and doctor.
    Patient listens for: doctor_joined, audit_*
    Doctor listens for:  payload_ready
    """
    s = get_session(session_id)
    queue = s.patient_queue if role == "patient" else s.doctor_queue

    async def stream():
        yield ": keepalive\n\n"

        # Replay current state on reconnect
        if role == "patient" and s.doc_public_key:
            yield _sse("doctor_joined", {"doc_public_key": s.doc_public_key})
        elif role == "doctor" and s.payload:
            yield _sse("payload_ready", s.payload)
            return  # doctor only needs the one event

        while time.time() < s.expiry:
            try:
                msg = await asyncio.wait_for(queue.get(), timeout=25.0)
                event = msg.pop("event")
                yield _sse(event, msg)
                # Doctor stream ends after payload delivery
                if role == "doctor" and event == "payload_ready":
                    return
            except asyncio.TimeoutError:
                yield ": keepalive\n\n"

        yield _sse("session_expired", {})

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )


@app.post("/session/{session_id}/payload")
async def receive_payload(session_id: str, req: PayloadRequest):
    """Patient sends encrypted payload after SAS verification + biometric approval."""
    s = get_session(session_id)

    if s.doc_public_key is None:
        raise HTTPException(400, "Doctor has not joined yet")
    if s.payload is not None:
        raise HTTPException(409, "Payload already delivered")

    # Sanity check: ephemeral keys in payload must match what was registered at session create
    if req.patient_eph_public != s.patient_eph_public:
        raise HTTPException(400, "patient_eph_public mismatch")

    payload = req.model_dump()
    s.payload = payload

    # Forward to doctor
    await s.doctor_queue.put({"event": "payload_ready", **payload})

    return {"status": "ok"}


@app.post("/session/{session_id}/audit")
async def log_audit(session_id: str, req: AuditRequest, request: Request):
    """Doctor reports an action (e.g. download_pdf). Relay records it and notifies patient."""
    s = get_session(session_id)

    entry = AuditEntry(action=req.action, device_hint=req.device_hint or request.client.host)
    s.audit_log.append(entry)

    await s.patient_queue.put({"event": "audit", **entry.to_dict()})

    return {"status": "ok"}


@app.get("/session/{session_id}/audit")
async def get_audit_log(session_id: str):
    """Patient app polls audit log (separate from SSE, for app resume scenarios)."""
    s = get_session(session_id)
    return {
        "session_id": session_id,
        "audit_log": [e.to_dict() for e in s.audit_log],
    }


@app.get("/s/{session_id}", response_class=HTMLResponse)
async def doctor_page(session_id: str):
    """Serve the doctor browser UI. Session validation is done client-side via /join."""
    html = (Path(__file__).parent / "static" / "doctor.html").read_text(encoding="utf-8")
    return HTMLResponse(html)


@app.get("/model/info")
async def model_info():
    """Returns model filename and byte size so the app can show a total in the progress bar."""
    if not MODEL_PATH or not os.path.exists(MODEL_PATH):
        raise HTTPException(404, "Model file not configured on this relay — set MODEL_PATH env var")
    return {"name": MODEL_FILE_NAME, "size": os.path.getsize(MODEL_PATH)}


@app.get("/model/download")
async def download_model():
    """Streams the model file. Content-Length lets the app track progress."""
    if not MODEL_PATH or not os.path.exists(MODEL_PATH):
        raise HTTPException(404, "Model file not configured on this relay — set MODEL_PATH env var")
    return FileResponse(
        MODEL_PATH,
        media_type="application/octet-stream",
        filename=MODEL_FILE_NAME,
    )


@app.get("/health")
async def health():
    return {"status": "ok", "active_sessions": len(sessions)}
