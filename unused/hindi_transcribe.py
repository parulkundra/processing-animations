#!/usr/bin/env python3
"""
Hindi Song Transcriber (with Demucs Vocal Separation)
======================================================
Pipeline:
  1. Demucs  — strips instruments, isolates vocals from the audio
  2. Whisper — transcribes the clean vocal track in Hindi
  3. indic-transliteration — converts Devanagari → Hinglish (romanized, literal)

Output format per line:
  [MM:SS.ms] transcribed text [MM:SS.ms]
    ^^ start ^^                ^^ end ^^

Requirements:
    pip install openai-whisper demucs indic-transliteration torch torchaudio

Usage:
    python hindi_transcribe.py song.mp3
    python hindi_transcribe.py song.mp3 --model large-v3 --output lyrics.txt
    python hindi_transcribe.py song.mp3 --no-demucs        # skip vocal separation
    python hindi_transcribe.py song.mp3 --word-level        # one word per line
    python hindi_transcribe.py song.mp3 --keep-vocals       # keep the separated vocal wav
"""

import argparse
import os
import sys
import json
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
    print("[WARNING] indic-transliteration not installed — Hinglish output will be skipped.")
    print("          pip install indic-transliteration\n")

# ── Whisper ────────────────────────────────────────────────────────────────────
try:
    import whisper
except ImportError:
    print("[ERROR] openai-whisper not installed.")
    print("        pip install openai-whisper")
    sys.exit(1)


# ──────────────────────────────────────────────────────────────────────────────
# Transliteration helpers
# ──────────────────────────────────────────────────────────────────────────────

def devanagari_to_hinglish(text: str) -> str:
    """Transliterate Devanagari → casual romanised Hinglish (literal, not translated)."""
    if not HAS_TRANSLIT or not text.strip():
        return text
    try:
        roman = transliterate(text, sanscript.DEVANAGARI, sanscript.ITRANS)
        return _simplify_itrans(roman)
    except Exception:
        return text


def _simplify_itrans(text: str) -> str:
    """
    Make ITRANS output look like natural Hinglish spelling.
    e.g. pyaar, dil, kya, aaj, shaam, baarish …
    Order matters — longer patterns must come first.
    """
    replacements = [
        ("~N", "n"), ("JN", "n"), (".m", "n"), ("M", "n"),  # anusvara / chandrabindu
        ("AA", "aa"), ("II", "ii"), ("UU", "uu"),            # long vowels
        ("Sh", "sh"), ("sh", "sh"),                          # sibilants
        ("Th", "th"), ("Dh", "dh"),                          # aspirated retroflexes
        ("kh", "kh"), ("gh", "gh"), ("ch", "ch"),
        ("jh", "jh"), ("ph", "ph"), ("bh", "bh"),
        ("T",  "t"),  ("D",  "d"),  ("N",  "n"),             # retroflex → simple
        ("H",  "h"),                                         # visarga
        ("OM", "om"),
    ]
    for old, new in replacements:
        text = text.replace(old, new)
    return text.strip()


# ──────────────────────────────────────────────────────────────────────────────
# Timestamp helper
# ──────────────────────────────────────────────────────────────────────────────

def ts(seconds: float) -> str:
    """Format float seconds → MM:SS.ms  e.g. 03:42.150"""
    m   = int(seconds // 60)
    s   = seconds % 60
    return f"{m:02d}:{s:06.3f}"


# ──────────────────────────────────────────────────────────────────────────────
# Demucs vocal separation
# ──────────────────────────────────────────────────────────────────────────────

def separate_vocals(audio_path: str, out_dir: str, model: str = "htdemucs") -> str:
    """
    Run Demucs to separate vocals from instrumentation.
    Returns the path to the isolated vocals WAV.

    Demucs writes to:  <out_dir>/<model>/<track_name>/vocals.wav
    """
    print(f"\n[DEMUCS] Separating vocals with model '{model}' …")
    print("[DEMUCS] Tip: pass --device cuda to Demucs env var for GPU acceleration.\n")

    cmd = [
        sys.executable, "-m", "demucs",
        "--two-stems", "vocals",   # split into vocals + no-vocals only (2x faster)
        "-n", model,
        "-o", out_dir,
        audio_path,
    ]

    try:
        result = subprocess.run(cmd, check=True, capture_output=False)
    except FileNotFoundError:
        print("[ERROR] Could not launch demucs. Install with: pip install demucs")
        sys.exit(1)
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] Demucs process exited with code {e.returncode}")
        sys.exit(1)

    # Locate vocals.wav — Demucs path: <out_dir>/<model>/<track_name>/vocals.wav
    track_name  = os.path.splitext(os.path.basename(audio_path))[0]
    vocals_path = os.path.join(out_dir, model, track_name, "vocals.wav")

    if not os.path.isfile(vocals_path):
        # Fallback: walk and find it
        for root, _, files in os.walk(out_dir):
            if "vocals.wav" in files:
                vocals_path = os.path.join(root, "vocals.wav")
                break
        else:
            print(f"[ERROR] vocals.wav not found under {out_dir}. Check Demucs output.")
            sys.exit(1)

    print(f"[DEMUCS] ✓ Vocals isolated → {vocals_path}")
    return vocals_path


# ──────────────────────────────────────────────────────────────────────────────
# Whisper transcription
# ──────────────────────────────────────────────────────────────────────────────

def transcribe_audio(audio_path: str, model_name: str = "medium") -> dict:
    """Transcribe with Whisper in Hindi, with word-level timestamps enabled."""
    print(f"\n[WHISPER] Loading model '{model_name}' …")
    model = whisper.load_model(model_name)

    print(f"[WHISPER] Transcribing: {os.path.basename(audio_path)}\n")
    result = model.transcribe(
        audio_path,
        language="hi",
        task="transcribe",
        word_timestamps=True,
        verbose=False,
        condition_on_previous_text=True,
        beam_size=5,
        best_of=5,
    )
    return result


# ──────────────────────────────────────────────────────────────────────────────
# Entry builders
# ──────────────────────────────────────────────────────────────────────────────

def get_segment_entries(result: dict) -> list[dict]:
    entries = []
    for seg in result.get("segments", []):
        hindi = seg.get("text", "").strip()
        entries.append({
            "start":    seg.get("start", 0.0),
            "end":      seg.get("end",   0.0),
            "hindi":    hindi,
            "hinglish": devanagari_to_hinglish(hindi),
        })
    return entries


def get_word_entries(result: dict) -> list[dict]:
    entries = []
    for seg in result.get("segments", []):
        for w in seg.get("words", []):
            hindi = w.get("word", "").strip()
            if not hindi:
                continue
            entries.append({
                "start":    w.get("start", 0.0),
                "end":      w.get("end",   0.0),
                "hindi":    hindi,
                "hinglish": devanagari_to_hinglish(hindi),
            })
    return entries


# ──────────────────────────────────────────────────────────────────────────────
# Core line renderer  →  [start] text [end]
# ──────────────────────────────────────────────────────────────────────────────

def render_line(entry: dict, script: str) -> str:
    """
    Returns a single line in the format:
        [MM:SS.ms] text [MM:SS.ms]
    """
    return f"[{ts(entry['start'])}] {entry[script]} [{ts(entry['end'])}]"


# ──────────────────────────────────────────────────────────────────────────────
# Output formatters
# ──────────────────────────────────────────────────────────────────────────────

def format_txt(result: dict, script: str, word_level: bool = False) -> str:
    """
    Plain text output for a single script (hindi or hinglish).
    Each line:  [MM:SS.ms] text [MM:SS.ms]
    """
    entries = get_word_entries(result) if word_level else get_segment_entries(result)
    label   = "HINDI (Devanagari)" if script == "hindi" else "HINGLISH (Romanized — literal)"

    lines = [
        "=" * 72,
        f"HINDI SONG TRANSCRIPTION — {label}",
        "Format: [start] text [end]",
        "=" * 72,
        "",
    ]

    for e in entries:
        lines.append(render_line(e, script))

    return "\n".join(lines)


def format_json_out(result: dict, script: str, word_level: bool = False) -> str:
    """Structured JSON for a single script (hindi or hinglish)."""
    entries = get_word_entries(result) if word_level else get_segment_entries(result)
    data = [
        {
            "start":    round(e["start"], 3),
            "end":      round(e["end"],   3),
            "start_ts": ts(e["start"]),
            "end_ts":   ts(e["end"]),
            "text":     e[script],
            "line":     render_line(e, script),
        }
        for e in entries
    ]
    return json.dumps(data, ensure_ascii=False, indent=2)


def format_srt(result: dict, script: str) -> str:
    """SRT subtitle file for a single script (hindi or hinglish)."""
    def srt_ts(s: float) -> str:
        h   = int(s // 3600)
        m   = int((s % 3600) // 60)
        sec = int(s % 60)
        ms  = int((s % 1) * 1000)
        return f"{h:02d}:{m:02d}:{sec:02d},{ms:03d}"

    blocks = []
    for i, e in enumerate(get_segment_entries(result), start=1):
        t1 = srt_ts(e["start"])
        t2 = srt_ts(e["end"])
        blocks.append(f"{i}\n{t1} --> {t2}\n{e[script]}")
    return "\n\n".join(blocks)


# ──────────────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────────────

WHISPER_MODELS = ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"]
DEMUCS_MODELS  = ["htdemucs", "htdemucs_ft", "mdx_extra", "mdx_extra_q"]


def main():
    parser = argparse.ArgumentParser(
        description="Transcribe Hindi songs: Demucs vocal separation → Whisper ASR → Hindi + Hinglish output",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("audio",
        help="Input audio file (mp3, wav, flac, m4a, …)")

    # ── Demucs options ─────────────────────────────────────────────────────────
    grp = parser.add_argument_group("Demucs (vocal separation)")
    grp.add_argument("--no-demucs", action="store_true",
        help="Skip vocal separation; transcribe raw audio directly")
    grp.add_argument("--demucs-model", default="htdemucs", choices=DEMUCS_MODELS,
        help="Demucs model to use (default: htdemucs). htdemucs_ft is finetuned and slightly better.")
    grp.add_argument("--keep-vocals", action="store_true",
        help="Save the extracted vocals WAV alongside the input file")

    # ── Whisper options ────────────────────────────────────────────────────────
    grp2 = parser.add_argument_group("Whisper (transcription)")
    grp2.add_argument("--model", default="large-v3", choices=WHISPER_MODELS,
        help="Whisper model size (default: medium). large-v3 is best for old/noisy songs.")

    # ── Output options ─────────────────────────────────────────────────────────
    grp3 = parser.add_argument_group("Output")
    grp3.add_argument("--format", default="txt", choices=["txt", "json", "srt"],
        help="Output format (default: txt)")
    grp3.add_argument("--output", "-o", default=None,
        help="Base output name. Two files are always saved: <name>_hindi.<fmt> and <name>_hinglish.<fmt>. Default: <song_name>_{script}.<fmt>")
    grp3.add_argument("--word-level", action="store_true",
        help="Emit one line per word instead of per sentence segment (txt & json only)")
    grp3.add_argument("--preview", action="store_true",
        help="Print the first 30 lines of output to stdout after saving")

    args = parser.parse_args()

    if not os.path.isfile(args.audio):
        print(f"[ERROR] File not found: {args.audio}")
        sys.exit(1)

    # ── Step 1: Demucs vocal separation ───────────────────────────────────────
    tmp_dir = None
    audio_to_transcribe = args.audio

    if not args.no_demucs:
        tmp_dir = tempfile.mkdtemp(prefix="demucs_out_")
        try:
            vocals_path = separate_vocals(
                args.audio,
                out_dir=tmp_dir,
                model=args.demucs_model,
            )
            if args.keep_vocals:
                base = os.path.splitext(args.audio)[0]
                dest = f"{base}_vocals.wav"
                shutil.copy2(vocals_path, dest)
                print(f"[INFO] Vocals copy saved → {dest}")
            audio_to_transcribe = vocals_path
        except SystemExit:
            raise
        except Exception as e:
            print(f"[WARNING] Demucs failed ({e}). Falling back to raw audio.")

    # ── Step 2: Whisper transcription ─────────────────────────────────────────
    result = transcribe_audio(audio_to_transcribe, model_name=args.model)

    # ── Cleanup Demucs temp files ──────────────────────────────────────────────
    if tmp_dir and os.path.isdir(tmp_dir):
        shutil.rmtree(tmp_dir, ignore_errors=True)

    # ── Step 3: Format & save two separate files (Hindi + Hinglish) ──────────
    fmt  = args.format
    base = os.path.splitext(args.audio)[0]

    # Derive output paths for each script
    if args.output:
        # Insert _hindi / _hinglish before the extension of the user-supplied path
        out_base, out_ext = os.path.splitext(args.output)
        if not out_ext:
            out_ext = f".{fmt}"
        hindi_path    = f"{out_base}_hindi{out_ext}"
        hinglish_path = f"{out_base}_hinglish{out_ext}"
    else:
        hindi_path    = f"{base}_hindi.{fmt}"
        hinglish_path = f"{base}_hinglish.{fmt}"

    outputs = {}   # script → (path, content)

    for script, path in [("hindi", hindi_path), ("hinglish", hinglish_path)]:
        if fmt == "txt":
            content = format_txt(result, script=script, word_level=args.word_level)
        elif fmt == "json":
            content = format_json_out(result, script=script, word_level=args.word_level)
        elif fmt == "srt":
            content = format_srt(result, script=script)

        with open(path, "w", encoding="utf-8") as f:
            f.write(content)

        outputs[script] = (path, content)
        print(f"[SAVED] {path}")

    print("\n[DONE] Two files written — Hindi and Hinglish separately.")

    if args.preview:
        for script, (path, content) in outputs.items():
            label = "Hindi" if script == "hindi" else "Hinglish"
            print(f"\n── {label} Preview ({path}) ─────────────────────────────────")
            print("\n".join(content.splitlines()[:15]))
            print("…")
        print("─────────────────────────────────────────────────────────────────")


if __name__ == "__main__":
    main()
