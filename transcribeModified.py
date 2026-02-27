# Very close but still not there, also in Hindi

import os
import sys
import subprocess
from faster_whisper import WhisperModel


def separate_vocals(audio_path):
    print("\n🔹 Separating vocals using Demucs...\n")

    command = [
        "demucs",
        "--two-stems=vocals",  # Only vocals + accompaniment
        audio_path
    ]

    subprocess.run(command, check=True)

    file_name = os.path.splitext(os.path.basename(audio_path))[0]
    vocals_path = os.path.join(
        "separated",
        "htdemucs",
        file_name,
        "vocals.wav"
    )

    if not os.path.exists(vocals_path):
        raise FileNotFoundError("Vocals file not found after Demucs processing.")

    print(f"✅ Vocals saved at: {vocals_path}")
    return vocals_path

def format_time(seconds):
    minutes = int(seconds // 60)
    secs = int(seconds % 60)
    millis = int((seconds - int(seconds)) * 100)
    return f"{minutes:02d}:{secs:02d}.{millis:02d}"
    
def transcribe_audio(audio_path):
    print("\n🔹 Loading Whisper model...\n")

    model_size = "large-v3"   # Best for old Hindi songs

    model = WhisperModel(
        model_size,
        device="cuda",        # change to "cpu" if no GPU
        compute_type="float16"  # use "int8" if low VRAM
    )

    print("\n🔹 Transcribing...\n")

    segments, info = model.transcribe(
        audio_path,
        language="hi",        # Force Hindi
        beam_size=8,
        word_timestamps=True
    )

    print(f"Detected language: {info.language}")

    full_text = ""
    word_output = ""

    for segment in segments:
        full_text += segment.text + "\n"

        if segment.words:
            for word in segment.words:
                start = format_time(word.start)
                end = format_time(word.end)
                word_output += f"[{start}]{word.word}[{end}]\n"

    with open("transcript.txt", "w", encoding="utf-8") as f:
        f.write(full_text)

    with open("word_timestamps.txt", "w", encoding="utf-8") as f:
        f.write(word_output)

    print("\n✅ Saved transcript.txt")
    print("✅ Saved word_timestamps.txt")


def main():
    if len(sys.argv) < 2:
        print("Usage: python hindi_song_pipeline.py song.mp3")
        return

    audio_path = sys.argv[1]

    vocals_path = separate_vocals(audio_path)
    transcribe_audio(vocals_path)


if __name__ == "__main__":
    main()