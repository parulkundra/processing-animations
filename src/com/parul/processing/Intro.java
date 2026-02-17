package com.parul.processing;

import com.hamoid.VideoExport;

import gifAnimation.GifMaker;
import processing.core.PApplet;
import processing.core.PImage;
import processing.sound.SoundFile;

public class Intro extends PApplet{
	
	GifMaker gifExport;	
	VideoExport videoExport;
	PImage bg;
	SoundFile file;
	int num = 60;
	float mx[] = new float[num];
	float my[] = new float[num];
	int width = 1920;
	int height = 1080;
	

    public static void main(String[] args) {
        PApplet.main("com.parul.processing.Intro");
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
		// Cycle through the array, using a different entry on each frame.
		// Using modulo (%) like this is faster than moving all the values over.
		int which = frameCount % num;
		mx[which] = mouseX;
		my[which] = mouseY;

		for (int i = 0; i < num; i++) {
			// which+1 is the smallest (the oldest in the array)
			int index = (which + 1 + i) % num;
			ellipse(mx[index], my[index], i, i);
		}
//		saveFrame("C:\\development\\processing\\output\\####.png");
		
		// Add current frame to the exported GIF
//		gifExport.addFrame();
		// Capture the current frame
		videoExport.saveFrame();
	}
    
//    public void keyPressed() {
//    	  // Stop recording and exit the sketch when a key is pressed (e.g., 'q')
//    	  if (key == 'q' || key == 'Q') {
//    	    videoExport.endMovie(); // Ensures metadata is written to the MP4 file
//    	    exit();
//    	  }
//    }
}