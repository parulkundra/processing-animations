package com.parul.processing;

import processing.core.PApplet;

public class Ball {
	  
	  PApplet pApplet;
	  int numBalls;
	  float x, y;
	  float diameter;
	  float vx = 0;
	  float vy = 0;
	  int id;
	  Ball[] others;
	  
	  float spring = 0.05f;
	  float gravity = 1f;
	  float friction = -0.9f;
	  
	  int r = 186;
	  int b = 151;
	  int g = 105;
			 
	  
	  Ball(PApplet pApplet, int numBalls, float xin, float yin, float din, int idin, Ball[] oin) {
		this.pApplet = pApplet;
		this.numBalls = numBalls;
	    x = xin;
	    y = yin;
	    diameter = din;
	    id = idin;
	    others = oin;
	    int ran = (int) pApplet.random(0, 100);
	    r = r+ran;
	    b = b+ran;
	    g = g+ran;
	  } 
	  
	  void collide() {
	    for (int i = id + 1; i < numBalls; i++) {
	      float dx = others[i].x - x;
	      float dy = others[i].y - y;
	      float distance = pApplet.sqrt(dx*dx + dy*dy);
	      float minDist = others[i].diameter/2 + diameter/2;
	      if (distance < minDist) { 
	        float angle = pApplet.atan2(dy, dx);
	        float targetX = x + pApplet.cos(angle) * minDist;
	        float targetY = y + pApplet.sin(angle) * minDist;
	        float ax = (targetX - others[i].x) * spring;
	        float ay = (targetY - others[i].y) * spring;
	        vx -= ax;
	        vy -= ay;
	        others[i].vx += ax;
	        others[i].vy += ay;
	      }
	    }   
	  }
	  
	  void move() {
	    vy += gravity;
	    x += vx;
	    y += vy;
	    if (x + diameter/2 > pApplet.width) {
	      x = pApplet.width - diameter/2;
	      vx *= friction; 
	    }
	    else if (x - diameter/2 < 0) {
	      x = diameter/2;
	      vx *= friction;
	    }
	    if (y + diameter/2 > pApplet.height) {
	      y = pApplet.height - diameter/2;
	      vy *= friction; 
	    } 
	    else if (y - diameter/2 < 0) {
	      y = diameter/2;
	      vy *= friction;
	    }
	  }
	  
	  void display() {
		  pApplet.fill(r, b, g);
		  pApplet.ellipse(x, y, diameter, diameter);
	  }
	}