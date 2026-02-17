package com.parul.processing;

import java.util.ArrayList;

import com.hamoid.VideoExport;

import gifAnimation.GifMaker;
import processing.core.PApplet;
import processing.core.PImage;
import processing.core.PVector;
import processing.sound.SoundFile;

public class EnhancedIntro extends PApplet{
	
	GifMaker gifExport;	
	VideoExport videoExport;
	PImage bg;
	SoundFile file;
	int width = 1920;
	int height = 1080;
	ArrayList<ParticleSystem> systems;
	

    public static void main(String[] args) {
        PApplet.main("com.parul.processing.EnhancedIntro");
    }

    public void settings(){
        size(width,height);
    }

    public void setup(){
//    	background(51);
    	bg = loadImage("C:\\development\\processing\\output\\let-us-karaoke-logo-transparent.png");
//    	frameRate(5);
    	noStroke();
    	fill(255, 153); 
    	systems = new ArrayList<ParticleSystem>();
    	
    	// To start recording a new GIF
    	gifExport = new GifMaker(this, "C:\\development\\processing\\output\\export.gif");
    	gifExport.setRepeat(0); // Endless loop
    	
    	videoExport = new VideoExport(this, "C:\\development\\processing\\output\\Intro.mp4"); //
    	videoExport.setFfmpegPath("C:\\Users\\parul\\AppData\\Local\\ffmpegio\\ffmpeg-downloader\\ffmpeg\\bin\\ffmpeg.exe");
//    	videoExport.setFrameRate(5);
    	videoExport.startMovie();
    	
    	file = new SoundFile(this, "C:\\development\\processing\\output\\introduction1.mp3");
    	file.play();
    }

	public void draw() {
	  background(51);
	  image(bg, (width - width/2)/2 , (height - height/2)/2);
	  for (ParticleSystem ps : systems) {
	    ps.run();
	    ps.addParticle();
	  }
	  if (systems.isEmpty()) {
	    fill(255);
	    textAlign(CENTER);
	    text("click mouse to add particle systems", width/2, height/2);
	  }
//		saveFrame("C:\\development\\processing\\output\\####.png");
		
		// Add current frame to the exported GIF
//		gifExport.addFrame();
		// Capture the current frame
		videoExport.saveFrame();
	}
    
	public void mousePressed() {
		systems.add(new ParticleSystem(this, 1, new PVector(mouseX, mouseY)));
	}
}