package com.parul.processing;

import processing.core.PApplet;
import processing.core.PVector;

public class Particle {
	
  PApplet pApplet;
  PVector position;
  PVector velocity;
  PVector acceleration;
  float lifespan;

  Particle(PApplet pApplet, PVector l) {
	this.pApplet = pApplet;
    acceleration = new PVector(0, 0.05f);
    velocity = new PVector(pApplet.random(-1, 1), pApplet.random(-2, 0));
    position = l.copy();
    lifespan = 255.0f;
  }

  void run() {
    update();
    display();
  }

  // Method to update position
  void update() {
    velocity.add(acceleration);
    position.add(velocity);
    lifespan -= 2.0;
  }

  // Method to display
  void display() {
	  pApplet.stroke(0, lifespan);
	  pApplet.fill(186,151,105, lifespan);
      pApplet.ellipse(position.x, position.y, 15f, 15f);
  }

  // Is the particle still useful?
  boolean isDead() {
    return (lifespan < 0.0);
  }
}