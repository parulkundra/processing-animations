import os
from moviepy.editor import *
from chatterbox.tts import ChatterboxTTS
from diffusers import AutoPipelineForText2Image
import torch
import torchaudio

story = [
"Once upon a time there was a little bunny named Benny.",
"Benny loved to hop through the meadow under the warm sun.",
"One evening the sky turned pink and the stars began to appear.",
"Benny felt sleepy and slowly hopped back home.",
"His mother tucked him into a cozy bed of soft leaves.",
"Benny closed his eyes and dreamed of wonderful adventures.",
"And soon the whole meadow fell asleep under the quiet moon."
]

title = "The Sleepy Bunny"

os.makedirs("images",exist_ok=True)
os.makedirs("audio",exist_ok=True)

print("Loading AI models...")

tts = ChatterboxTTS.from_pretrained('cuda')

pipe = AutoPipelineForText2Image.from_pretrained(
    "stabilityai/sdxl-turbo",
    torch_dtype=torch.float16,
    variant="fp16"
)

pipe = pipe.to("cuda")

clips = []

for i,scene in enumerate(story):

    print("Scene",i)

    prompt = f"""
storybook illustration for children,
{scene},
soft pastel colors,
bedtime story illustration,
gentle lighting
"""

    img = pipe(prompt,
        num_inference_steps=2,
        guidance_scale=0.0).images[0]

    img_path = f"images/scene{i}.png"
    img.save(img_path)

    audio = tts.generate(scene)

    audio_path = f"audio/scene{i}.wav"
    
    # Remove batch dimension if present
    if audio.dim() == 3:
        audio = audio.squeeze(0)
    
    # Ensure shape = [channels, samples]
    if audio.dim() == 1:
        audio = audio.unsqueeze(0)
    
    torchaudio.save(
        audio_path,
        audio.cpu(),
        24000
    )

    image_clip = ImageClip(img_path).set_duration(6)

    narration = AudioFileClip(audio_path)

    clip = image_clip.set_audio(narration)

    clips.append(clip)

video = concatenate_videoclips(clips)

music = AudioFileClip("music/lullaby.mp3").volumex(0.2)

final_audio = CompositeAudioClip([music,video.audio])

video = video.set_audio(final_audio)

video.write_videofile("bedtime_story1.mp4",fps=24)