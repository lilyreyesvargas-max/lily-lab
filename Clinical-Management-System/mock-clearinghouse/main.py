"""
Mock Clearinghouse REST API — Clinical Management System
Simulates EDI clearinghouse endpoints for local sandbox testing.
"""
import os
import json
import time
import logging
from pathlib import Path
from datetime import datetime

from fastapi import FastAPI, UploadFile, File, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

# ── Config ────────────────────────────────────────────────────────────────────
EDI_VERBOSE = os.getenv("EDI_VERBOSE", "0") == "1"
EDI_BASE_DIR = Path(os.getenv("EDI_BASE_DIR", "/edi"))

LOG_LEVEL = logging.DEBUG if EDI_VERBOSE else logging.INFO
logging.basicConfig(level=LOG_LEVEL, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("mock-clearinghouse")

app = FastAPI(title="Mock EDI Clearinghouse", version="1.0.0")


# ── Startup ───────────────────────────────────────────────────────────────────
@app.on_event("startup")
def startup_event():
    for subdir in ["out/logs", "out/837", "out/270", "in/835", "in/271"]:
        path = EDI_BASE_DIR / subdir
        path.mkdir(parents=True, exist_ok=True)
    logger.info("Mock Clearinghouse started. EDI base: %s", EDI_BASE_DIR)


# ── Models ────────────────────────────────────────────────────────────────────
class ContentBody(BaseModel):
    content: str = ""


class EligibilityBody(BaseModel):
    content: str = ""
    member_id: str = ""
    insurer_id: str = ""


# ── Helpers ───────────────────────────────────────────────────────────────────
def _ts() -> str:
    return datetime.utcnow().strftime("%Y%m%d_%H%M%S_%f")


def _control_number() -> str:
    return f"MOCK{int(time.time() * 1000) % 10**9:09d}"


DUMMY_835 = """\
ISA*00*          *00*          *ZZ*CLRHOUSE       *ZZ*SENDCLINIC     *240102*0900*^*00501*000000003*0*T*:~
GS*HP*CLRHOUSE*SENDCLINIC*20240102*0900*3*X*005010X221A1~
ST*835*0003~
BPR*I*500.00*C*ACH*CTX*01*999988880*DA*1234567890*1512345678**01*999988880*DA*0123456789*20240102~
TRN*1*CHECK12345*1512345678~
DTM*405*20240102~
N1*PR*BLUE CROSS PPO*XV*BCBS001~
N1*PE*MAIN CLINIC HQ*XX*1234567890~
LX*1~
CLP*CLAIM001*1*500.00*500.00**MC*ICN001*11~
NM1*QC*1*DOE*JOHN****MI*INS123456~
SVC*HC:99213*150.00*150.00**1~
SVC*HC:85025*50.00*50.00**1~
SE*13*0003~
GE*1*3~
IEA*1*000000003~
"""

DUMMY_271 = """\
ISA*00*          *00*          *ZZ*CLRHOUSE       *ZZ*SENDCLINIC     *240101*1201*^*00501*000000005*0*T*:~
GS*HB*CLRHOUSE*SENDCLINIC*20240101*1201*5*X*005010X279A1~
ST*271*0005*005010X279A1~
BHT*0022*11*10001234*20240101*1201~
HL*1**20*1~
NM1*PR*2*BLUE CROSS PPO*****PI*BCBS001~
HL*2*1*21*1~
NM1*1P*2*MAIN CLINIC HQ*****XX*1234567890~
HL*3*2*22*0~
TRN*2*93175-012547*9877281234~
NM1*IL*1*DOE*JOHN*M***MI*INS123456~
DMG*D8*19800101*M~
DTP*291*D8*20240101~
EB*1*FAM*30*CI*PPO GOLD PLAN~
EB*C*FAM**CI**23*1500.00~
EB*G*FAM**CI**23*750.00~
SE*18*0005~
GE*1*5~
IEA*1*000000005~
"""


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    return {"status": "ok", "service": "mock-clearinghouse"}


@app.post("/submit-837")
async def submit_837(body: ContentBody = None, file: UploadFile = File(None)):
    """Accept 837P claim file and store in /edi/out/logs/."""
    try:
        if file is not None:
            content = (await file.read()).decode("utf-8", errors="replace")
            filename = file.filename or f"837_{_ts()}.txt"
        elif body and body.content:
            content = body.content
            filename = f"837_{_ts()}.txt"
        else:
            raise HTTPException(status_code=400, detail="No content or file provided")

        log_path = EDI_BASE_DIR / "out" / "logs" / filename
        log_path.write_text(content, encoding="utf-8")
        ctrl = _control_number()
        logger.info("837 received → %s (control=%s)", log_path.name, ctrl)

        return {
            "status": "accepted",
            "control_number": ctrl,
            "message": "837P received and queued for processing",
            "filename": log_path.name,
        }
    except HTTPException:
        raise
    except Exception as exc:
        logger.error("submit-837 error: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


@app.get("/get-835")
def get_835():
    """Return a 835 ERA. Uses first file in /edi/in/835/ or dummy."""
    in_dir = EDI_BASE_DIR / "in" / "835"
    files = list(in_dir.glob("*.txt"))
    if files:
        content = files[0].read_text(encoding="utf-8")
        logger.info("Serving 835 from %s", files[0].name)
    else:
        content = DUMMY_835
        logger.info("Serving dummy 835")
    return {"status": "ok", "content": content}


@app.post("/eligibility/270")
async def eligibility_270(body: EligibilityBody = None, file: UploadFile = File(None)):
    """Accept 270 eligibility request; return 271 response."""
    try:
        if file is not None:
            content = (await file.read()).decode("utf-8", errors="replace")
        elif body and body.content:
            content = body.content
        else:
            content = ""

        # Save 270 outbound
        out_path = EDI_BASE_DIR / "out" / "270" / f"270_{_ts()}.txt"
        out_path.write_text(content, encoding="utf-8")

        # Save 271 inbound
        in_path = EDI_BASE_DIR / "in" / "271" / f"271_{_ts()}.txt"
        in_path.write_text(DUMMY_271, encoding="utf-8")

        logger.info("270 received → 271 generated (%s)", in_path.name)

        return {
            "status": "ok",
            "content": DUMMY_271,
            "eligible": True,
            "plan": "PPO Gold",
            "deductible": 1500.00,
            "deductible_met": 750.00,
            "copay": 30.00,
            "message": "Member is eligible",
        }
    except Exception as exc:
        logger.error("eligibility/270 error: %s", exc)
        raise HTTPException(status_code=500, detail=str(exc))


# ── Entry point ───────────────────────────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=18080)
