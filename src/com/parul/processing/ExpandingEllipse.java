package com.parul.processing;

import java.util.Random;

import com.hamoid.VideoExport;

import gifAnimation.GifMaker;
import processing.core.PApplet;

public class ExpandingEllipse extends PApplet{
	
	GifMaker gifExport;	
	VideoExport videoExport;
	static final int lowRBG = 100;
	static final int highRBG = 255;
	static final int initialRadius = 10;
	static int radius = initialRadius;
	static int maxRadius = 2000;

    public static void main(String[] args) {
        PApplet.main("com.parul.processing.ExpandingEllipse");
    }

    public void settings(){
        size(1920,1080);
    }

    public void setup(){
    	background(highRBG);
    	frameRate(5);
    	
    	// To start recording a new GIF
    	gifExport = new GifMaker(this, "C:\\development\\processing\\output\\export.gif");
    	gifExport.setRepeat(0); // Endless loop
    	
    	videoExport = new VideoExport(this, "C:\\development\\processing\\output\\processing-movie.mp4"); //
    	videoExport.setFfmpegPath("C:\\Users\\parul\\AppData\\Local\\ffmpegio\\ffmpeg-downloader\\ffmpeg\\bin\\ffmpeg.exe");
    	videoExport.setFrameRate(5);
    	videoExport.startMovie();
    }

    public void draw(){
    	int second = second();
    	Random r = new Random();
    	noStroke();
    	fill(r.nextInt(highRBG-lowRBG) + lowRBG, r.nextInt(highRBG-lowRBG) + lowRBG, r.nextInt(highRBG-lowRBG) + lowRBG);
    	ellipse(width/2,height/2,radius,radius);
    	radius = radius + 10;
	    if (radius == maxRadius) {
	    	radius = initialRadius;
	    }
	    maxRadius--;
	    saveFrame("C:\\development\\processing\\output\\####.png");
	    // Add current frame to the exported GIF
	    gifExport.addFrame();
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