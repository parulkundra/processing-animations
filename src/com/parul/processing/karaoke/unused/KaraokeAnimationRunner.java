package com.parul.processing.karaoke.unused;

import java.util.Random;

import com.hamoid.VideoExport;

import gifAnimation.GifMaker;
import processing.core.PApplet;

public class KaraokeAnimationRunner extends PApplet{
	
	VideoExport videoExport;
	static final int lowRBG = 100;
	static final int highRBG = 255;
	static final int initialRadius = 10;
	static int radius = initialRadius;
	static int maxRadius = 2000;

    public static void main(String[] args) {
        PApplet.main("com.parul.processing.karaoke.KaraokeRunner");
    }

    public void settings(){
        size(1920,1080);
    }

    public void setup(){
    	background(highRBG);
//    	frameRate(5);
    	
//    	videoExport = new VideoExport(this, "C:\\development\\test\\karaokeTest.mp4"); //
//    	videoExport.setFfmpegPath("C:\\development\\ffmpeg\\ffmpeg-master-latest-win64-gpl-shared\\bin\\ffmpeg.exe");
//    	videoExport.setFrameRate(5);
//    	videoExport.startMovie();
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

	    // Capture the current frame
//	    videoExport.saveFrame();
    }

}