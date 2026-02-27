import os
import sys
import subprocess
from faster_whisper import WhisperModel
from indic_transliteration.sanscript import transliterate
from indic_transliteration import sanscript


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


def transliterate_to_hinglish(text):
    return transliterate(
        text,
        sanscript.DEVANAGARI,
        sanscript.ITRANS
    )


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

    hindi_text = ""
    hinglish_text = ""

    for segment in segments:
        hindi_text += segment.text + "\n"
        hinglish_text += transliterate_to_hinglish(segment.text) + "\n"

    # Save Hindi
    with open("transcript_hindi.txt", "w", encoding="utf-8") as f:
        f.write(hindi_text)

    # Save Hinglish
    with open("transcript_hinglish.txt", "w", encoding="utf-8") as f:
        f.write(hinglish_text)

    print("\n✅ Hindi transcript saved → transcript_hindi.txt")
    print("✅ Hinglish transcript saved → transcript_hinglish.txt")

    print("\n--- Hinglish Output Preview ---\n")
    print(hinglish_text)


def main():
    if len(sys.argv) < 2:
        print("Usage: python hindi_song_pipeline.py song.mp3")
        return

    audio_path = sys.argv[1]
    vocals_path = separate_vocals(audio_path)
    transcribe_audio(vocals_path)


if __name__ == "__main__":
    main()