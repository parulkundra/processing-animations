package com.parul.processing;

import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PImage;

public class IntroThumb extends PApplet{
	
	
	String title = "Ahista";
	String song = "Ahista Ahista";
	String singer = "Kazinama & Musarrat Nazir";
	String folder = "C:\\development\\music\\AhistaAhista\\";
	PImage bg;
	PImage watermark;
	int width = 1920;
	int height = 1080;
	int widthMid = (width - width/2)/2;
	int heightMid = (height - height/2)/2 -100;
	PFont mono;
	

    public static void main(String[] args) {
        PApplet.main("com.parul.processing.IntroThumb");
    }

    public void settings(){
        size(width,height);
    }

    public void setup(){
//    	background(51);
    	bg = loadImage(folder + "thumb.jpg");
    	watermark = loadImage(folder + "let-us-karaoke-logo-transparent.png");
//    	frameRate(5);
    	noStroke();
//    	fill(186,151,105); 
    	noLoop();
    	mono = createFont("Fresh Lemonade - Personal Use.otf", 150);
    	
    }

	public void draw() {
		background(51);
		image(bg, 0 , 0);
		textAlign(CENTER);
		textFont(mono);
		text(song, width/2 , heightMid);
		mono = createFont("Midnight Angel.ttf", 100);
		textFont(mono);
		text("Kazinama", width/2 , heightMid + 150);
		text("and", width/2 , heightMid + 300);
		text("Nazir", width/2 , heightMid + 450);
		mono = createFont("Midnight Angel.ttf", 250);
		textFont(mono);
		text("Karaoke", width/2 , heightMid + 650);
		image(watermark, width-300, height-233);
		saveFrame(folder + title + "_thumb.png");
		
	}
    
//    public void keyPressed() {
//    	  // Stop recording and exit the sketch when a key is pressed (e.g., 'q')
//    	  if (key == 'q' || key == 'Q') {
//    	    videoExport.endMovie(); // Ensures metadata is written to the MP4 file
//    	    exit();
//    	  }
//    }
}