# https://chatgpt.com/share/69a1cdd8-2708-8001-94d2-731042022b7e
# Used HPSS separation, didnt work

import librosa
import numpy as np
import soundfile as sf
import torch
from faster_whisper import WhisperModel
from scipy.signal import medfilt

INPUT_AUDIO = "input.mp3"
DEVICE = "cuda" if torch.cuda.is_available() else "cpu"


# ---------------------------------------
# STEP 1: HPSS Separation
# ---------------------------------------
def hpss_separate(input_audio):
    print("Applying HPSS separation...")

    y, sr = librosa.load(input_audio, sr=None, mono=True)

    harmonic, percussive = librosa.effects.hpss(y)

    sf.write("harmonic.wav", harmonic, sr)
    return "harmonic.wav", sr


# ---------------------------------------
# STEP 2: Mild Noise Reduction (Spectral Gate)
# ---------------------------------------
def mild_denoise(input_wav, sr):
    print("Applying mild noise reduction...")

    y, _ = librosa.load(input_wav, sr=sr)

    S = librosa.stft(y)
    mag, phase = np.abs(S), np.angle(S)

    # Estimate noise floor (very conservative)
    noise_floor = np.percentile(mag, 20, axis=1, keepdims=True)

    # Soft mask
    mask = mag > noise_floor * 1.2
    mask = medfilt(mask.astype(float), kernel_size=(1, 5))

    cleaned = mask * mag * np.exp(1j * phase)
    y_clean = librosa.istft(cleaned)

    sf.write("clean_vocals.wav", y_clean, sr)
    return "clean_vocals.wav"


# ---------------------------------------
# STEP 3: Transcription
# ---------------------------------------
def transcribe(audio_file):
    print("Transcribing with Faster-Whisper large-v3...")

    model = WhisperModel(
        "large-v3",
        device=DEVICE,
        compute_type="float16" if DEVICE == "cuda" else "int8"
    )

    segments, _ = model.transcribe(
        audio_file,
        language="hi",
        beam_size=5,
        temperature=0.0,
        condition_on_previous_text=False,
        vad_filter=True
    )

    return segments


# ---------------------------------------
# STEP 4: Print Output
# ---------------------------------------
def print_output(segments):
    print("\n--- TRANSCRIPT ---\n")

    for s in segments:
        start = round(s.start, 2)
        end = round(s.end, 2)
        text = s.text.strip()

        print(f"[{start}]{text}[{end}]")


# ---------------------------------------
# MAIN
# ---------------------------------------
if __name__ == "__main__":
    harmonic, sr = hpss_separate(INPUT_AUDIO)
    cleaned = mild_denoise(harmonic, sr)
    segments = transcribe(cleaned)
    print_output(segments)