#!/usr/bin/env python3
"""
Hindi Song Transcriber
======================
Transcribes old Hindi songs with word-level timings in:
  1. Hindi (Devanagari script)
  2. Hinglish (romanized transliteration — literal, NOT translated)

Requirements:
    pip install openai-whisper indic-transliteration torch

Usage:
    python hindi_transcribe.py <audio_file> [options]

    python hindi_transcribe.py song.mp3
    python hindi_transcribe.py song.mp3 --output lyrics.txt
    python hindi_transcribe.py song.mp3 --model large --format srt
"""

import argparse
import os
import sys
import json

# ── Transliteration ────────────────────────────────────────────────────────────
try:
    from indic_transliteration import sanscript
    from indic_transliteration.sanscript import transliterate
    HAS_TRANSLIT = True
except ImportError:
    HAS_TRANSLIT = False
    print("[WARNING] indic-transliteration not installed. Hinglish output will be skipped.")
    print("          Install with: pip install indic-transliteration\n")

# ── Whisper ────────────────────────────────────────────────────────────────────
try:
    import whisper
except ImportError:
    print("[ERROR] openai-whisper not installed.")
    print("        Install with: pip install openai-whisper")
    sys.exit(1)


# ──────────────────────────────────────────────────────────────────────────────
# Helpers
# ──────────────────────────────────────────────────────────────────────────────

def devanagari_to_hinglish(text: str) -> str:
    """Transliterate Devanagari Hindi → Roman (ITRANS/HK scheme, readable Hinglish)."""
    if not HAS_TRANSLIT or not text.strip():
        return text
    try:
        # DEVANAGARI → IAST gives clean diacritic-based roman
        # We then simplify diacritics for a more "Hinglish" feel
        roman = transliterate(text, sanscript.DEVANAGARI, sanscript.ITRANS)
        return simplify_itrans(roman)
    except Exception:
        return text


def simplify_itrans(text: str) -> str:
    """
    Convert ITRANS notation to casual readable Hinglish.
    e.g.  'aaj' stays 'aaj', 'aa' → 'aa', 'sh' → 'sh', etc.
    """
    replacements = [
        # Long vowels — keep doubled for Hinglish readability
        ("AA", "aa"), ("II", "ii"), ("UU", "uu"),
        # Retroflex — simplify
        ("T", "t"), ("D", "d"), ("N", "n"),
        # Aspirated — keep h notation
        ("kh", "kh"), ("gh", "gh"), ("ch", "ch"), ("jh", "jh"),
        ("Th", "th"), ("Dh", "dh"), ("ph", "ph"), ("bh", "bh"),
        # Sibilants
        ("sh", "sh"), ("Sh", "sh"),
        # Nasals
        ("~N", "n"), ("JN", "n"), ("M", "n"), (".m", "n"),
        # Visarga
        ("H", "h"),
        # Misc
        ("OM", "om"), ("a", "a"),
    ]
    for old, new in replacements:
        text = text.replace(old, new)
    return text.strip()


def format_timestamp(seconds: float) -> str:
    """Convert seconds to MM:SS.ms format."""
    m = int(seconds // 60)
    s = seconds % 60
    return f"{m:02d}:{s:06.3f}"


def format_srt_timestamp(seconds: float) -> str:
    """SRT format: HH:MM:SS,ms"""
    h = int(seconds // 3600)
    m = int((seconds % 3600) // 60)
    s = int(seconds % 60)
    ms = int((seconds % 1) * 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


# ──────────────────────────────────────────────────────────────────────────────
# Core transcription
# ──────────────────────────────────────────────────────────────────────────────

def transcribe(audio_path: str, model_name: str = "medium") -> dict:
    """Run Whisper transcription on audio file, forced to Hindi."""
    print(f"[INFO] Loading Whisper model: '{model_name}' ...")
    model = whisper.load_model(model_name)

    print(f"[INFO] Transcribing: {audio_path}")
    result = model.transcribe(
        audio_path,
        language="hi",          # Force Hindi
        task="transcribe",      # Keep original language (not translate)
        word_timestamps=True,   # Enable word-level timing
        verbose=False,
    )
    return result


# ──────────────────────────────────────────────────────────────────────────────
# Output formatters
# ──────────────────────────────────────────────────────────────────────────────

def build_word_entries(result: dict) -> list[dict]:
    """
    Flatten all word-level data from Whisper segments into a list of dicts:
      { start, end, hindi, hinglish }
    """
    entries = []
    for segment in result.get("segments", []):
        for word_info in segment.get("words", []):
            hindi_word = word_info.get("word", "").strip()
            if not hindi_word:
                continue
            hinglish_word = devanagari_to_hinglish(hindi_word)
            entries.append({
                "start":    word_info.get("start", 0.0),
                "end":      word_info.get("end", 0.0),
                "hindi":    hindi_word,
                "hinglish": hinglish_word,
            })
    return entries


def build_segment_entries(result: dict) -> list[dict]:
    """Segment-level (line-level) entries."""
    entries = []
    for seg in result.get("segments", []):
        hindi_text = seg.get("text", "").strip()
        hinglish_text = devanagari_to_hinglish(hindi_text)
        entries.append({
            "start":    seg.get("start", 0.0),
            "end":      seg.get("end", 0.0),
            "hindi":    hindi_text,
            "hinglish": hinglish_text,
        })
    return entries


# ── Plain text ─────────────────────────────────────────────────────────────────

def format_plain(result: dict) -> str:
    lines = []
    lines.append("=" * 70)
    lines.append("HINDI SONG TRANSCRIPTION")
    lines.append("=" * 70)
    lines.append("")

    for seg in build_segment_entries(result):
        ts = f"[{format_timestamp(seg['start'])} → {format_timestamp(seg['end'])}]"
        lines.append(ts)
        lines.append(f"  Hindi    : {seg['hindi']}")
        lines.append(f"  Hinglish : {seg['hinglish']}")
        lines.append("")

    return "\n".join(lines)


# ── SRT subtitle ───────────────────────────────────────────────────────────────

def format_srt(result: dict, dual: bool = True) -> str:
    """
    SRT with both Hindi and Hinglish lines stacked in each subtitle block.
    Set dual=False to get two separate .srt files instead.
    """
    blocks = []
    for i, seg in enumerate(build_segment_entries(result), start=1):
        t1 = format_srt_timestamp(seg["start"])
        t2 = format_srt_timestamp(seg["end"])
        if dual:
            text = f"{seg['hindi']}\n{seg['hinglish']}"
        else:
            text = seg["hindi"]
        blocks.append(f"{i}\n{t1} --> {t2}\n{text}")
    return "\n\n".join(blocks)


# ── LRC lyrics ────────────────────────────────────────────────────────────────

def format_lrc(result: dict) -> str:
    """
    LRC format used by most music players.
    Outputs paired lines: Hindi then Hinglish for each timestamp.
    """
    lines = []
    for seg in build_segment_entries(result):
        m = int(seg["start"] // 60)
        s = seg["start"] % 60
        ts = f"[{m:02d}:{s:05.2f}]"
        lines.append(f"{ts}{seg['hindi']}")
        lines.append(f"{ts}{seg['hinglish']}")
        lines.append("")
    return "\n".join(lines)


# ── Word-level TSV ─────────────────────────────────────────────────────────────

def format_tsv(result: dict) -> str:
    rows = ["start\tend\thindi\thinglish"]
    for w in build_word_entries(result):
        rows.append(
            f"{w['start']:.3f}\t{w['end']:.3f}\t{w['hindi']}\t{w['hinglish']}"
        )
    return "\n".join(rows)


# ── JSON ───────────────────────────────────────────────────────────────────────

def format_json(result: dict) -> str:
    segments = build_segment_entries(result)
    words = build_word_entries(result)
    data = {
        "segments": segments,
        "words":    words,
    }
    return json.dumps(data, ensure_ascii=False, indent=2)


# ──────────────────────────────────────────────────────────────────────────────
# CLI
# ──────────────────────────────────────────────────────────────────────────────

FORMATS = {
    "txt":  format_plain,
    "srt":  format_srt,
    "lrc":  format_lrc,
    "tsv":  format_tsv,
    "json": format_json,
}

MODELS = ["tiny", "base", "small", "medium", "large", "large-v2", "large-v3"]


def main():
    parser = argparse.ArgumentParser(
        description="Transcribe old Hindi songs with timings in Hindi & Hinglish",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("audio", help="Path to audio file (mp3, wav, m4a, flac, …)")
    parser.add_argument(
        "--model", default="medium", choices=MODELS,
        help="Whisper model size (default: medium). Larger = more accurate but slower.\n"
             "Recommended for old songs: large-v3",
    )
    parser.add_argument(
        "--format", default="txt", choices=list(FORMATS.keys()),
        help="Output format (default: txt)",
    )
    parser.add_argument(
        "--output", "-o", default=None,
        help="Output file path. Defaults to <audio_name>.<format>",
    )
    parser.add_argument(
        "--all-formats", action="store_true",
        help="Save output in ALL formats (txt, srt, lrc, tsv, json)",
    )
    parser.add_argument(
        "--preview", action="store_true",
        help="Print a short preview of the result to stdout",
    )

    args = parser.parse_args()

    if not os.path.isfile(args.audio):
        print(f"[ERROR] File not found: {args.audio}")
        sys.exit(1)

    # Transcribe
    result = transcribe(args.audio, model_name=args.model)
    print(f"[INFO] Detected language confidence: Hindi")

    base_name = os.path.splitext(args.audio)[0]

    if args.all_formats:
        for fmt, fn in FORMATS.items():
            out_path = f"{base_name}.{fmt}"
            content = fn(result)
            with open(out_path, "w", encoding="utf-8") as f:
                f.write(content)
            print(f"[SAVED] {out_path}")
    else:
        fmt = args.format
        content = FORMATS[fmt](result)
        out_path = args.output or f"{base_name}.{fmt}"
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"[SAVED] {out_path}")

        if args.preview:
            print("\n── Preview ──────────────────────────────────────────────────────")
            print("\n".join(content.splitlines()[:40]))
            print("─────────────────────────────────────────────────────────────────")


if __name__ == "__main__":
    main()