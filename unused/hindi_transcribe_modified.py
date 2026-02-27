#!/usr/bin/env python3
"""
Hindi Song Transcriber
======================
Pipeline:
  1. Demucs  — isolates vocals from music/instruments
  2. Whisper — transcribes the clean vocal track in Hindi
  3. indic-transliteration — converts Devanagari → Hinglish (romanized, literal)

Output: two lyrics files per song —
    <song>_hindi.txt      →  [MM:SS.ms]कहीं दूर जब दिन ढल जाए[MM:SS.ms]
    <song>_hinglish.txt   →  [MM:SS.ms]kahiin door jab din dhal jaae[MM:SS.ms]

Requirements:
    pip install openai-whisper demucs indic-transliteration torch torchaudio
    brew install ffmpeg   # macOS
    sudo apt install ffmpeg  # Linux

Usage:
    python hindi_transcribe.py song.mp3
    python hindi_transcribe.py song.mp3 --model medium
    python hindi_transcribe.py song.mp3 --no-demucs
    python hindi_transcribe.py song.mp3 --keep-vocals
"""

import argparse
import os
import sys
import shutil
import subprocess
import tempfile

# ── indic-transliteration ──────────────────────────────────────────────────────
try:
    from indic_transliteration import sanscript
    from indic_transliteration.sanscript import transliterate
    HAS_TRANSLIT = True
except ImportError:
    HAS_TRANSLIT = False
    print("[WARNING] indic-transliteration not installed — Hinglish file will mirror Hindi.")
    print("          pip install indic-transliteration\n")

# ── Whisper ────────────────────────────────────────────────────────────────────
try:
    import whisper
except ImportError:
    print("[ERROR] openai-whisper not installed.  pip install openai-whisper")
    sys.exit(1)


# ──────────────────────────────────────────────────────────────────────────────
# Transliteration
# ──────────────────────────────────────────────────────────────────────────────

def to_hinglish(text: str) -> str:
    """Devanagari → casual romanised Hinglish (literal transliteration, not translation)."""
    if not HAS_TRANSLIT or not text.strip():
        return text
    try:
        roman = transliterate(text, sanscript.DEVANAGARI, sanscript.ITRANS)
        return _clean_itrans(roman)
    except Exception:
        return text


def _clean_itrans(text: str) -> str:
    """Simplify raw ITRANS into readable Hinglish (pyaar, dil, aaj, shaam …)."""
    # Order matters — longer patterns first
    for old, new in [
        ("~N", "n"), ("JN", "n"), (".m", "n"), ("M", "n"),
        ("AA", "aa"), ("II", "ii"), ("UU", "uu"),
        ("Sh", "sh"), ("Th", "th"), ("Dh", "dh"),
        ("kh", "kh"), ("gh", "gh"), ("ch", "ch"),
        ("jh", "jh"), ("ph", "ph"), ("bh", "bh"),
        ("T",  "t"),  ("D",  "d"),  ("N",  "n"),
        ("H",  "h"),  ("OM", "om"),
    ]:
        text = text.replace(old, new)
    return text.strip()


# ──────────────────────────────────────────────────────────────────────────────
# Timestamp
# ──────────────────────────────────────────────────────────────────────────────

def fmt_ts(seconds: float) -> str:
    """Float seconds → MM:SS.ms  e.g. 03:42.150"""
    m = int(seconds // 60)
    s = seconds % 60
    return f"{m:02d}:{s:06.3f}"


# ──────────────────────────────────────────────────────────────────────────────
# Demucs vocal separation
# ──────────────────────────────────────────────────────────────────────────────

def separate_vocals(audio_path: str, out_dir: str, model: str = "htdemucs") -> str:
    """
    Run Demucs to strip instruments and return path to isolated vocals WAV.
    Output layout: <out_dir>/<model>/<track_name>/vocals.wav
    """
    print(f"\n[DEMUCS] Separating vocals with model '{model}' …")

    cmd = [
        sys.executable, "-m", "demucs",
        "--two-stems", "vocals",
        "-n", model,
        "-o", out_dir,
        audio_path,
    ]

    try:
        subprocess.run(cmd, check=True)
    except FileNotFoundError:
        print("[ERROR] demucs not found.  pip install demucs")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Demucs exited with code {e.returncode}")
        sys.exit(1)

    # Locate vocals.wav
    track_name  = os.path.splitext(os.path.basename(audio_path))[0]
    vocals_path = os.path.join(out_dir, model, track_name, "vocals.wav")

    if not os.path.isfile(vocals_path):
        for root, _, files in os.walk(out_dir):
            if "vocals.wav" in files:
                vocals_path = os.path.join(root, "vocals.wav")
                break
        else:
            print(f"[ERROR] vocals.wav not found under {out_dir}")
            sys.exit(1)

    print(f"[DEMUCS] ✓ Vocals isolated → {vocals_path}")
    return vocals_path


# ──────────────────────────────────────────────────────────────────────────────
# Whisper transcription
# ──────────────────────────────────────────────────────────────────────────────

def transcribe(audio_path: str, model_name: str) -> dict:
    print(f"\n[WHISPER] Loading model '{model_name}' …")
    model = whisper.load_model(model_name)
    print(f"[WHISPER] Transcribing: {os.path.basename(audio_path)}\n")
    return model.transcribe(
        audio_path,
        language="hi",
        task="transcribe",
        word_timestamps=True,
        verbose=False,
        condition_on_previous_text=True,
        beam_size=5,
        best_of=5,
    )


# ──────────────────────────────────────────────────────────────────────────────
# Lyrics writer
# ──────────────────────────────────────────────────────────────────────────────

def write_lyrics(result: dict, hindi_path: str, hinglish_path: str) -> None:
    """
    Write two lyrics files.  Each line format:
        [MM:SS.ms]text[MM:SS.ms]
    """
    hindi_lines    = []
    hinglish_lines = []

    for seg in result.get("segments", []):
        hindi_text    = seg.get("text", "").strip()
        hinglish_text = to_hinglish(hindi_text)
        start         = fmt_ts(seg.get("start", 0.0))
        end           = fmt_ts(seg.get("end",   0.0))

        hindi_lines.append(f"[{start}]{hindi_text}[{end}]")
        hinglish_lines.append(f"[{start}]{hinglish_text}[{end}]")

    with open(hindi_path, "w", encoding="utf-8") as f:
        f.write("\n".join(hindi_lines))

    with open(hinglish_path, "w", encoding="utf-8") as f:
        f.write("\n".join(hinglish_lines))


# ──────────────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────────────

WHISPER_MODELS = ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"]
DEMUCS_MODELS  = ["htdemucs", "htdemucs_ft", "mdx_extra", "mdx_extra_q"]


def main():
    parser = argparse.ArgumentParser(
        description="Transcribe Hindi songs → Hindi + Hinglish lyrics files",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("audio",
        help="Input audio file (mp3, wav, flac, m4a, …)")
    parser.add_argument("--model", default="large-v3", choices=WHISPER_MODELS,
        help="Whisper model (default: large-v3)")
    parser.add_argument("--no-demucs", action="store_true",
        help="Skip vocal separation and transcribe raw audio directly")
    parser.add_argument("--demucs-model", default="htdemucs", choices=DEMUCS_MODELS,
        help="Demucs model (default: htdemucs). htdemucs_ft is slightly more accurate.")
    parser.add_argument("--keep-vocals", action="store_true",
        help="Save the extracted vocals WAV alongside the input file")

    args = parser.parse_args()

    if not os.path.isfile(args.audio):
        print(f"[ERROR] File not found: {args.audio}")
        sys.exit(1)

    # ── Step 1: Vocal separation ───────────────────────────────────────────────
    tmp_dir = None
    audio_to_transcribe = args.audio

    if not args.no_demucs:
        tmp_dir = tempfile.mkdtemp(prefix="demucs_")
        try:
            vocals_path = separate_vocals(args.audio, tmp_dir, model=args.demucs_model)
            if args.keep_vocals:
                dest = os.path.splitext(args.audio)[0] + "_vocals.wav"
                shutil.copy2(vocals_path, dest)
                print(f"[INFO] Vocals saved → {dest}")
            audio_to_transcribe = vocals_path
        except SystemExit:
            raise
        except Exception as e:
            print(f"[WARNING] Demucs failed ({e}), using raw audio.")

    # ── Step 2: Transcribe ─────────────────────────────────────────────────────
    result = transcribe(audio_to_transcribe, model_name=args.model)

    if tmp_dir:
        shutil.rmtree(tmp_dir, ignore_errors=True)

    # ── Step 3: Write lyrics files ─────────────────────────────────────────────
    base         = os.path.splitext(args.audio)[0]
    hindi_path   = f"{base}_hindi.txt"
    hinglish_path = f"{base}_hinglish.txt"

    write_lyrics(result, hindi_path, hinglish_path)

    print(f"\n[DONE]")
    print(f"  Hindi    → {hindi_path}")
    print(f"  Hinglish → {hinglish_path}")


if __name__ == "__main__":
    main()
