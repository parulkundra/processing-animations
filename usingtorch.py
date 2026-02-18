import torch
import torchaudio
import re
import stable_whisper as stable
# Assumes a text file 'transcript.txt' and 'audio.wav' exist
# Using a Wav2Vec2 based alignment approach
model = stable.load_model("medium")
folder = "C:/development/music/AhistaAhista/"
with open(folder + "raw_lyrics.txt", "r", encoding="utf-8") as f:
    transcript = f.read().strip()

#transcript = re.sub(r"[^\w\s']", "", transcript.lower())

#transcript = re.sub(r"[^\w\s']", "", transcript)
#transcript = re.sub(r"\s+", " ", transcript).strip()

result = model.align(
    folder + "vocals.mp3",
    transcript,
    language="Hindi",
    original_split= True
)
lines = []
# Word-level timestamps
for segment in result.segments:
    if segment.words:
        for word in segment.words:
            lines.append(word.word + "_" + str(word.start) + "_" + str(word.end))
            print(word.word, word.start, word.end)

result.to_ass(folder + "ts_lyrics.ass")

with open(folder + "ts_lyrics.txt", "w") as file:
    file.write("\n".join(lines)) # Joins all items with a newline separator

print("generated!")