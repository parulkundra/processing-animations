import os
from moviepy.editor import *
from chatterbox.tts import ChatterboxTTS
from diffusers import ControlNetModel, StableDiffusionXLControlNetPipeline, MotionAdapter, AnimateDiffPipeline
import torch
import cv2
import numpy as np
import torchaudio
import torchaudio.functional as F
from PIL import Image
from diffusers.utils import export_to_video

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

controlnet = ControlNetModel.from_pretrained(
    "diffusers/controlnet-canny-sdxl-1.0",
    torch_dtype=torch.float16
)

pipe = StableDiffusionXLControlNetPipeline.from_pretrained(
    "stabilityai/stable-diffusion-xl-base-1.0",
    controlnet=controlnet,
    torch_dtype=torch.float16,
    variant="fp16"
)

# Load motion adapter
adapter_pipe = MotionAdapter.from_pretrained(
    "guoyww/animatediff-motion-adapter-v1-5",
    torch_dtype=torch.float16
)

# Load base model + adapter
video_pipe = AnimateDiffPipeline.from_pretrained(
    "runwayml/stable-diffusion-v1-5",
    motion_adapter=adapter_pipe,
    torch_dtype=torch.float16
)

pipe = pipe.to("cuda")
video_pipe = video_pipe.to("cuda")

video_pipe.enable_vae_slicing()
video_pipe.enable_attention_slicing()

def create_canny(image):
    image = np.array(image)
    edges = cv2.Canny(image, 100, 200)
    edges = np.stack([edges]*3, axis=2)
    edges = Image.fromarray(edges)
    return edges



clips = []

for i,scene in enumerate(story):

    print("Scene",i)
    
    img_path = f"images/scene{i}.png"
    audio_path = f"audio/scene{i}.wav"
    video_path = f"videos/scene{i}.mp4"
    
    prompt = f"""
        storybook illustration for children,
        {scene},
        soft pastel colors,
        bedtime story illustration,
        gentle lighting
        """

    negative_prompt = """
        deformed, mutated, extra limbs, extra arms,
        extra legs, extra fingers, distorted face,
        crooked eyes, bad anatomy, blurry
        """

    ''' control_image = Image.new("RGB", (1024,1024), "white")
        control = create_canny(control_image)

        img = pipe(prompt,
                    negative_prompt=negative_prompt,
                    image=control,
                    height=1024,
                    width=1024,
                    num_inference_steps=30,
                    guidance_scale=7.0,
                    controlnet_conditioning_scale=0.7
                ).images[0]

        
        img.save(img_path)

        audio = tts.generate(scene)
        
        # Remove batch dimension if present
        if audio.dim() == 3:
            audio = audio.squeeze(0)
        
        # Ensure shape = [channels, samples]
        if audio.dim() == 1:
            audio = audio.unsqueeze(0)
        
        torchaudio.save(
            audio_path,
            audio.cpu(),
            20000
        ) '''
    
    ''' image = Image.open(img_path)
        
        output = video_pipe(
            image=image,
            prompt=prompt,
            num_frames=16,
            num_inference_steps=20,
            guidance_scale=7
            )

        frames = output.frames[0]
        
        processed_frames = [np.array(f) for f in frames]

        clip = ImageSequenceClip(processed_frames, fps=8)
        clip.write_videofile(video_path, codec="libx264")
        
        print('saved!')'''
        
    video_clip = VideoFileClip(video_path)

    audio_clip = AudioFileClip(audio_path)
    
    video_clip = video_clip.set_audio(audio_clip)

    #image_clip = ImageClip(img_path).set_duration(6).resize(height=720).fadein(1).fadeout(1)

    #narration = AudioFileClip(audio_path)

    #clip = image_clip.set_audio(narration)

    clips.append(video_clip)

video = concatenate_videoclips(clips)

music = AudioFileClip("music/lullaby.mp3").volumex(0.2)

final_audio = CompositeAudioClip([music,video.audio])

video = video.set_audio(final_audio)

video.write_videofile("bedtime_story1.mp4",fps=24)

