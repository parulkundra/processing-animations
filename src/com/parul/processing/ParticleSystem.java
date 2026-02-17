package com.parul.processing;

import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PVector;

public class ParticleSystem {
	
  PApplet pApplet;
  ArrayList<Particle> particles;    // An arraylist for all the particles
  PVector origin;                   // An origin point for where particles are birthed

  ParticleSystem(PApplet pApplet, int num, PVector v) {
	this.pApplet = pApplet;
    particles = new ArrayList<Particle>();   // Initialize the arraylist
    origin = v.copy();                        // Store the origin point
    for (int i = 0; i < num; i++) {
      particles.add(new Particle(pApplet, origin));    // Add "num" amount of particles to the arraylist
    }
  }


  void run() {
    // Cycle through the ArrayList backwards, because we are deleting while iterating
    for (int i = particles.size()-1; i >= 0; i--) {
      Particle p = particles.get(i);
      p.run();
      if (p.isDead()) {
        particles.remove(i);
      }
    }
  }

  void addParticle() {
    Particle p;
    // Add either a Particle or CrazyParticle to the system
    if ((pApplet.random(0, 2)) == 0) {
      p = new Particle(pApplet, origin);
    } 
    else {
      p = new CrazyParticle(pApplet, origin);
    }
    particles.add(p);
  }

  void addParticle(Particle p) {
    particles.add(p);
  }

  // A method to test if the particle system still has particles
  boolean dead() {
    return particles.isEmpty();
  }
}