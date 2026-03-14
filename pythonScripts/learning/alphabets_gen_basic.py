import os
import string
import subprocess
from multiprocessing import Pool
from moviepy.editor import *
from chatterbox.tts import ChatterboxTTS
from PIL import Image, ImageDraw, ImageFont
from tqdm import tqdm
import torchaudio

letters = list(string.ascii_uppercase)

words = {
"A":"Apple","B":"Ball","C":"Cat","D":"Dog","E":"Elephant",
"F":"Fish","G":"Grapes","H":"Hat","I":"Ice Cream","J":"Juice",
"K":"Kite","L":"Lion","M":"Monkey","N":"Nest","O":"Orange",
"P":"Parrot","Q":"Queen","R":"Rabbit","S":"Sun","T":"Tiger",
"U":"Umbrella","V":"Violin","W":"Whale","X":"Xylophone",
"Y":"Yacht","Z":"Zebra"
}

for folder in ["audio","clips","scenes"]:
    os.makedirs(folder,exist_ok=True)

tts = ChatterboxTTS.from_pretrained('cuda')


# --------------------------------
# Generate Voice
# --------------------------------

def generate_audio(letter):

    text = f"{letter}! {letter} is for {words[letter]}!"
    path = f"audio/{letter}.wav"

    wav = tts.generate(text)
    
    # Remove batch dimension if present
    if wav.dim() == 3:
        wav = wav.squeeze(0)
    
    # Ensure shape = [channels, samples]
    if wav.dim() == 1:
        wav = wav.unsqueeze(0)
    
    torchaudio.save(
        path,
        wav.cpu(),
        20000
    )

    return path


# --------------------------------
# Create Manim Scene
# --------------------------------

def generate_scene(letter):

    scene_file = f"scenes/{letter}.py"

    code = f'''
from manim import *
import random

class Letter(Scene):

    def construct(self):

        bg = Rectangle(
            width=16,
            height=9,
            fill_color=BLUE_E,
            fill_opacity=1
        )

        self.add(bg)

        letter = Text("{letter}", font_size=250, weight=BOLD)

        letter.move_to(UP*5)

        self.play(FadeIn(letter))

        self.play(
            letter.animate.move_to(DOWN*2),
            rate_func=rate_functions.ease_out_bounce,
            run_time=2
        )

        sparkles = VGroup(*[
            Dot(radius=0.05,color=YELLOW)
            for _ in range(20)
        ])

        sparkles.move_to(letter)

        self.play(
            LaggedStart(*[
                dot.animate.shift(
                    RIGHT*random.uniform(-1,1) +
                    UP*random.uniform(-1,1)
                )
                for dot in sparkles
            ])
        )

        self.wait(1)
'''

    with open(scene_file,"w") as f:
        f.write(code)
    
    subprocess.run(
    [
        "manim",
        "-qh",
        scene_file,
        "Letter",
        "-o",
        f"{letter}.mp4",
        "--media_dir",
        "media"
    ],
    check=True
)


# --------------------------------
# Compose Clip
# --------------------------------

def build_clip(letter):

    video = VideoFileClip(
       f"media/videos/{letter}/1080p60/{letter}.mp4"
    )

    image = (
        ImageClip(f"images/{letter}.png")
        .resize(height=350)
        .set_duration(video.duration)
        .set_position(("right","center"))
        .crossfadein(1)
    )

    narration = AudioFileClip(f"audio/{letter}.wav")

    bounce = AudioFileClip("sfx/bounce.mp3").volumex(0.4)

    music = (
        AudioFileClip("music/kids_music.mp3")
        .subclip(0,video.duration)
        .volumex(0.2)
    )

    audio = CompositeAudioClip([music,bounce,narration])

    clip = CompositeVideoClip(
        [video,image]
    ).set_audio(audio)

    out = f"clips/{letter}.mp4"

    clip.write_videofile(out,fps=30)

    return out


# --------------------------------
# Full Letter Pipeline
# --------------------------------

def process_letter(letter):

    print("Creating",letter)

    generate_audio(letter)
    generate_scene(letter)

    return build_clip(letter)


# --------------------------------
# Render A → Z in parallel
# --------------------------------

print("Rendering letters...")

clip_paths = []

for letter in tqdm(letters):
    print(letter)
    clip_paths.append(process_letter(letter))


# --------------------------------
# Merge Alphabet
# --------------------------------

print("Merging final video...")

clips = [VideoFileClip(c) for c in clip_paths]

final = concatenate_videoclips(clips)

final.write_videofile(
    "alphabet_video.mp4",
    codec="libx264",
    fps=30
)


# --------------------------------
# Create YouTube Shorts Version
# --------------------------------

short = final.resize(height=1920)

short.write_videofile(
    "alphabet_shorts.mp4",
    fps=30
)


# --------------------------------
# Create Thumbnail
# --------------------------------

print("Creating thumbnail...")

img = Image.new("RGB",(1280,720),(255,200,0))
draw = ImageDraw.Draw(img)

font = ImageFont.load_default()

draw.text(
    (200,300),
    "Learn A to Z Alphabet!",
    fill=(0,0,0),
    font=font
)

img.save("thumbnail.png")

print("DONE!")