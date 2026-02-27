import os
import sys
import subprocess
from faster_whisper import WhisperModel
from aksharamukha import transliterate


# -----------------------------
# Step 1: Separate Vocals
# -----------------------------
def separate_vocals(audio_path):
    print("\n🔹 Separating vocals using Demucs...\n")

    subprocess.run(
        ["demucs", "--two-stems=vocals", audio_path],
        check=True
    )

    file_name = os.path.splitext(os.path.basename(audio_path))[0]

    vocals_path = os.path.join(
        "separated",
        "htdemucs",
        file_name,
        "vocals.wav"
    )

    if not os.path.exists(vocals_path):
        raise FileNotFoundError("Vocals file not found.")

    return vocals_path


# -----------------------------
# Step 2: Better Hinglish
# -----------------------------
def to_hinglish(text):
    # Devanagari → Roman (natural Hindi style)
    return transliterate.process(
        "Devanagari",
        "HK",
        text
    ).lower().replace("ii", "i").replace("aa", "a")


# -----------------------------
# Step 3: Transcription
# -----------------------------
def transcribe_audio(audio_path):
    print("\n🔹 Loading Whisper model...\n")

    model = WhisperModel(
        "large-v3",
        device="cuda",      # change to "cpu" if needed
        compute_type="float16"
    )

    print("\n🔹 Transcribing...\n")

    segments, info = model.transcribe(
        audio_path,
        language="hi",
        beam_size=8,
        word_timestamps=True
    )

    with open("transcript_hinglish_timed.txt", "w", encoding="utf-8") as f:

        for segment in segments:
            hinglish_line = to_hinglish(segment.text)

            # Segment timestamp
            line_output = f"[{segment.start:.2f}s - {segment.end:.2f}s] {hinglish_line}\n"
            f.write(line_output)

            print(line_output.strip())

            # Word-level timestamps
            if segment.words:
                for word in segment.words:
                    word_hinglish = to_hinglish(word.word)
                    word_line = f"    {word_hinglish} ({word.start:.2f}-{word.end:.2f})\n"
                    f.write(word_line)

    print("\n✅ Saved → transcript_hinglish_timed.txt")


# -----------------------------
# Main
# -----------------------------
def main():
    if len(sys.argv) < 2:
        print("Usage: python hindi_song_pipeline.py song.mp3")
        return

    audio_path = sys.argv[1]

    vocals_path = separate_vocals(audio_path)
    transcribe_audio(vocals_path)


if __name__ == "__main__":
    main()