"""Generate the voice-guidance clips in app/src/main/res/raw with ElevenLabs text-to-speech.

Run: python tools/gen_voice_prompts.py [--force]
Key: ELEVENLABS_API_KEY in the environment or in .env at the repo root (never commit it).
Existing clips are skipped unless --force. The chime is synthesised locally, no API call.
"""
import json
import math
import os
import struct
import sys
import urllib.request
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
MODEL = "eleven_v3"
VOICES = {"vi": "3QLrbl9MwD1RvYnIwoTN",  # Dương, Vietnamese (Southern)
          "en": "EXAVITQu4vr4xnSDxMaL"}  # Sarah, English (premade)

# clip name -> (Vietnamese, English). Names are the VoiceGuide.Cue values in lower case.
LINES = {
    "start": ("Bắt đầu dẫn đường.", "Starting navigation."),
    "go_straight": ("Đi thẳng.", "Go straight."),
    "turn_left_soon": ("Phía trước rẽ trái.", "Turn left ahead."),
    "turn_right_soon": ("Phía trước rẽ phải.", "Turn right ahead."),
    "turn_left": ("Rẽ trái.", "Turn left."),
    "turn_right": ("Rẽ phải.", "Turn right."),
    "slight_left": ("Chếch sang trái.", "Bear left."),
    "slight_right": ("Chếch sang phải.", "Bear right."),
    "wrong_way": ("Bạn đang đi ngược hướng, hãy quay lại.", "You're going the wrong way, please turn around."),
    "off_route": ("Bạn đã đi lệch đường, đang tìm đường mới.", "You're off route, finding a new route."),
    "rerouted": ("Đã có đường mới, hãy đi theo đường trên bản đồ.", "New route found, follow the line on the map."),
    "back_on_route": ("Bạn đã về đúng đường.", "You're back on route."),
    "elevator_up": ("Hãy đi thang máy lên tầng trên.", "Take the elevator up."),
    "elevator_down": ("Hãy đi thang máy xuống tầng dưới.", "Take the elevator down."),
    "stairs_up": ("Hãy đi thang bộ lên tầng trên.", "Take the stairs up."),
    "stairs_down": ("Hãy đi thang bộ xuống tầng dưới.", "Take the stairs down."),
    "arriving": ("Sắp tới nơi.", "Almost there."),
    "arrived": ("Bạn đã tới nơi.", "You have arrived."),
}


def api_key():
    key = os.environ.get("ELEVENLABS_API_KEY")
    env = ROOT / ".env"
    if not key and env.exists():
        for line in env.read_text(encoding="utf-8").splitlines():
            if line.startswith("ELEVENLABS_API_KEY="):
                key = line.split("=", 1)[1].strip().strip('"')
    if not key:
        sys.exit("ELEVENLABS_API_KEY missing (environment or .env)")
    return key


def tts(key, voice, text, lang):
    req = urllib.request.Request(
        f"https://api.elevenlabs.io/v1/text-to-speech/{voice}?output_format=mp3_44100_128",
        data=json.dumps({"text": text, "model_id": MODEL, "language_code": lang,
                         "voice_settings": {"stability": 1.0}}).encode(),  # "Robust": same delivery every clip
        headers={"xi-api-key": key, "Content-Type": "application/json", "Accept": "audio/mpeg"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read()


def chime(path):
    """Two short sine notes (E6 then A6) with a fast decay: a neutral 'ding' before each cue."""
    rate, out = 44100, bytearray()
    for freq, dur in ((1318.5, 0.12), (1760.0, 0.22)):
        n = int(rate * dur)
        for i in range(n):
            env = math.exp(-6 * i / n) * min(1, i / 200)  # 5 ms attack, no click
            out += struct.pack("<h", int(0.35 * 32767 * env * math.sin(2 * math.pi * freq * i / rate)))
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate); w.writeframes(bytes(out))


def main():
    sys.stdout.reconfigure(encoding="utf-8")  # Vietnamese text on a Windows console
    force = "--force" in sys.argv
    RAW.mkdir(parents=True, exist_ok=True)
    if force or not (RAW / "voice_chime.wav").exists():
        chime(RAW / "voice_chime.wav")
    key = None
    for name, texts in LINES.items():
        for lang, text in zip(("vi", "en"), texts):
            path = RAW / f"voice_{name}_{lang}.mp3"
            if path.exists() and not force:
                continue
            key = key or api_key()
            path.write_bytes(tts(key, VOICES[lang], text, lang))
            print(f"{path.name}  {path.stat().st_size} B  {text}")


if __name__ == "__main__":
    main()
