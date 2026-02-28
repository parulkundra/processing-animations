package com.parul.processing.karaoke.unused;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Random;

import processing.core.PApplet;
import processing.core.PFont;

/**
 * Renders individual video frames for the karaoke output.
 *
 * Background: twinkling stars on a black sky.
 * Each star has a fixed position, random size, and a sine-wave brightness
 * that pulses independently — giving a natural, non-uniform twinkle effect.
 *
 * Foreground: 4 lines of lyrics centred on screen with cyan wipe effect.
 *
 * Rendering order each frame:
 *   1. Black background
 *   2. Stars (drawn at brightness calculated from currentTimeMs)
 *   3. Lyrics (drawn on top of stars)
 */
public class FrameRendererProcessing {

    // ── Lyric colours ──────────────────────────────────────────────────────────
    private static final Color SUNG_COLOR     = new Color(0, 220, 255);  // bright cyan
    private static final Color ACTIVE_COLOR   = new Color(0, 220, 255);  // bright cyan
    private static final Color UPCOMING_COLOR = Color.WHITE;

    // ── Lyric layout ───────────────────────────────────────────────────────────
    private static final float FONT_SIZE_RATIO    = 0.055f;
    private static final float LINE_SPACING_RATIO = 1.6f;

    // ── Star configuration ─────────────────────────────────────────────────────
    private static  int   STAR_COUNT         = 200;   // number of stars
    private static  float STAR_MIN_BRIGHTNESS = 0.15f; // never fully dark (0.0–1.0)
    private static  float STAR_MAX_BRIGHTNESS = 1.0f;  // peak brightness
    // Twinkle speed range in cycles per second (higher = faster flicker)
    private static  float TWINKLE_SPEED_MIN  = 0.3f;
    private static  float TWINKLE_SPEED_MAX  = 1.5f;

    // ── Star data arrays (parallel arrays — one entry per star) ───────────────
    private final int[]   starX;          // fixed pixel X position
    private final int[]   starY;          // fixed pixel Y position
    private final int[]   starSize;       // diameter in pixels (1, 2, or 3)
    private final float[] starPhase;      // sine phase offset (radians) so stars don't sync
    private final float[] starSpeed;      // twinkle cycles per second
    private final int[]   starColorR;     // base red   component (varies slightly per star)
    private final int[]   starColorG;     // base green component
    private final int[]   starColorB;     // base blue  component

    // ── Pre-computed layout ────────────────────────────────────────────────────
    private  final PApplet pApplet;
    private final int  width;
    private final int  height;
    private final PFont lyricFont;
    private final int  lineHeight;
    private final int  blockHeight;
    private final int  blockStartY;
    private int linesPerVerse = 6;

    public FrameRendererProcessing(PApplet pApplet, int width, int height, boolean isFastSong, PFont font, Integer linesPerV) throws Exception {
        if (isFastSong) {
            STAR_COUNT         = 400;   // number of stars
            STAR_MIN_BRIGHTNESS = 0.5f; // never fully dark (0.0–1.0)
            STAR_MAX_BRIGHTNESS = 1.9f;  // peak brightness
            // Twinkle speed range in cycles per second (higher = faster flicker)
            TWINKLE_SPEED_MIN  = 1.7f;
            TWINKLE_SPEED_MAX  = 5.0f;
        }
        
        this.pApplet     = pApplet;
        this.width       = width;
        this.height      = height;
        int fontSize     = Math.max(24, (int)(height * FONT_SIZE_RATIO));
        if (font == null) {
        	this.lyricFont   = pApplet.createFont("Arial", fontSize);
        } else {
        	this.lyricFont = font;
        }
        if (linesPerV != null) {
        	this.linesPerVerse = linesPerV;
        }
        
        this.lineHeight  = (int)(fontSize * LINE_SPACING_RATIO);
        this.blockHeight = lineHeight * linesPerVerse;
        this.blockStartY = (height - blockHeight) / 2 + fontSize;

        // ── Initialise stars ───────────────────────────────────────────────────
        // Use a fixed seed so stars are in the same position every frame
        // (they only change brightness, not position)
        Random rng = new Random(42L);

        starX      = new int[STAR_COUNT];
        starY      = new int[STAR_COUNT];
        starSize   = new int[STAR_COUNT];
        starPhase  = new float[STAR_COUNT];
        starSpeed  = new float[STAR_COUNT];
        starColorR = new int[STAR_COUNT];
        starColorG = new int[STAR_COUNT];
        starColorB = new int[STAR_COUNT];

        for (int i = 0; i < STAR_COUNT; i++) {
            starX[i]     = rng.nextInt(width);
            starY[i]     = rng.nextInt(height);

            // Weighted distribution: mostly small stars, fewer large ones
            // ~60% size-1, ~30% size-2, ~10% size-3
            int roll = rng.nextInt(10);
            starSize[i]  = (roll < 6) ? 1 : (roll < 9) ? 2 : 3;

            // Random phase offset (0 → 2π) so each star starts its twinkle cycle
            // at a different point — prevents all stars pulsing in unison
            starPhase[i] = rng.nextFloat() * (float)(2 * Math.PI);

            // Random twinkle speed within configured range
            starSpeed[i] = TWINKLE_SPEED_MIN
                         + rng.nextFloat() * (TWINKLE_SPEED_MAX - TWINKLE_SPEED_MIN);

            // Slight colour variation: most stars are white/blue-white,
            // a few are warm yellowish (like real stars)
            int type = rng.nextInt(10);
            if (type < 7) {
                // Blue-white star (cool)
                starColorR[i] = 200 + rng.nextInt(56);  // 200–255
                starColorG[i] = 200 + rng.nextInt(56);  // 200–255
                starColorB[i] = 255;                     // always full blue
            } else if (type < 9) {
                // Pure white star
                starColorR[i] = 255;
                starColorG[i] = 255;
                starColorB[i] = 255;
            } else {
                // Warm yellow-white star (rare)
                starColorR[i] = 255;
                starColorG[i] = 220 + rng.nextInt(36);  // 220–255
                starColorB[i] = 150 + rng.nextInt(80);  // 150–230
            }
        }
        System.out.printf("Initialised %d stars at %dx%d%n", STAR_COUNT, width, height);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    public void renderFrame(List<LyricLine> lines, long currentTimeMs) {
//        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
//        Graphics2D g = img.createGraphics();
//
//        pApplet.hint(1);
//        pApplet.hint(2);
//        pApplet.hint(0);
//        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
//        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
//        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
//
        
        pApplet.color(0);
        pApplet.rect(0, 0, width, height);

        // ── 2. Draw twinkling stars ────────────────────────────────────────────
        drawStars(currentTimeMs);

        // ── 3. Draw lyrics on top ──────────────────────────────────────────────
        if (!lines.isEmpty()) {
            int activeIdx     = LRCParser.getActiveLineIndex(lines, currentTimeMs);
            int verseStartIdx = (activeIdx < 0) ? 0
                                : (activeIdx / linesPerVerse) * linesPerVerse;
            drawVerse(lines, verseStartIdx, activeIdx, currentTimeMs);
        }

//        g.dispose();
//        return img;
    }

    // ── Star rendering ─────────────────────────────────────────────────────────

    /**
     * Draws all stars at their current brightness for the given playback time.
     *
     * Brightness formula per star:
     *   rawSine   = sin(timeSeconds * speed + phase)    → range -1.0 to +1.0
     *   normalized = (rawSine + 1.0) / 2.0              → range  0.0 to  1.0
     *   brightness = MIN + normalized * (MAX - MIN)     → range  MIN to  MAX
     *
     * The sine wave produces a smooth, continuous pulse. Because each star has
     * a unique phase and speed, they all twinkle independently of each other.
     */
    private void drawStars(long currentTimeMs) {
        double timeSec = currentTimeMs / 1000.0;

        for (int i = 0; i < STAR_COUNT; i++) {
            // Calculate brightness using sine wave
            double rawSine    = Math.sin(timeSec * starSpeed[i] + starPhase[i]);
            double normalized = (rawSine + 1.0) / 2.0;                        // 0.0 → 1.0
            double brightness = STAR_MIN_BRIGHTNESS
                              + normalized * (STAR_MAX_BRIGHTNESS - STAR_MIN_BRIGHTNESS);

            // Apply brightness to the star's base colour
            int r = (int)(starColorR[i] * brightness);
            int gv = (int)(starColorG[i] * brightness);
            int b = (int)(starColorB[i] * brightness);

            // Clamp to valid colour range
            r  = Math.min(255, Math.max(0, r));
            gv = Math.min(255, Math.max(0, gv));
            b  = Math.min(255, Math.max(0, b));

            pApplet.color(r, gv, b);

            int sz = starSize[i];
            if (sz == 1) {
                // Single pixel — fastest to draw
            	pApplet.line(starX[i], starY[i], starX[i], starY[i]);
            } else {
                // Filled circle for larger stars
                // Centre the circle on the star's coordinate
                int offset = sz / 2;
                pApplet.ellipse(starX[i] - offset, starY[i] - offset, sz, sz);

                // Add a soft cross-shaped "glint" for the largest stars (size 3)
                // to give them a sparkle look
                if (sz == 3 && brightness > 0.7) {
                    int alpha = (int)((brightness - 0.7) / 0.3 * 120); // 0–120
                    alpha = Math.min(120, Math.max(0, alpha));
                    pApplet.color(r, gv, b, alpha);
                    // Horizontal arm
                    pApplet.line(starX[i] - 3, starY[i], starX[i] + 3, starY[i]);
                    // Vertical arm
                    pApplet.line(starX[i], starY[i] - 3, starX[i], starY[i] + 3);
                }
            }
        }
    }

    // ── Lyric rendering ────────────────────────────────────────────────────────

    private void drawVerse(List<LyricLine> lines,
                           int verseStartIdx, int activeIdx, long currentTimeMs) {
    	pApplet.textFont(lyricFont);

        for (int slot = 0; slot < linesPerVerse; slot++) {
            int lineIdx = verseStartIdx + slot;
            if (lineIdx >= lines.size()) break;

            LyricLine line = lines.get(lineIdx);
            int y = blockStartY + slot * lineHeight;

            if (lineIdx < activeIdx) {
                drawCenteredText(line.getText(), y, 0, 220, 255);
            } else if (lineIdx == activeIdx) {
                drawWipeLine(line, y, currentTimeMs);
            } else {
                drawCenteredText(line.getText(), y, 255, 255, 255);
            }
        }
    }

    /**
     * Draws the active line with a pixel-accurate left-to-right cyan wipe.
     * See FrameRenderer history for full algorithm explanation.
     */
    private void drawWipeLine(LyricLine line, int y, long currentTimeMs) {
        String[] words  = line.words;
        String fullText = line.getText();
        int totalWidth  = (int) pApplet.textWidth(fullText);
        int lineStartX  = (width - totalWidth) / 2;
        int spaceWidth  = (int) pApplet.textWidth(" ");

        int[] wordWidths = new int[words.length];
        for (int i = 0; i < words.length; i++) {
            wordWidths[i] = (int) pApplet.textWidth(words[i]);
        }

        // Pass 1: white base
        pApplet.color(255);
        pApplet.text(fullText, lineStartX, y);

        if (currentTimeMs < line.startTimeMs) return;

        // Find active word
        int currentWord = 0;
        for (int i = line.wordHighlightMs.length - 1; i >= 0; i--) {
            if (currentTimeMs >= line.wordHighlightMs[i]) {
                currentWord = i;
                break;
            }
        }

        // Fraction through current word's time slot
        long slotStart, slotEnd;
        if (line.hasExplicitEndTimes) {
            // MODE 1: explicit end times per word
            slotStart = line.wordHighlightMs[currentWord];
            slotEnd   = line.wordEndMs[currentWord];
        } else {
            // MODE 2 or MODE 3: end = start of next word (or line end)
            slotStart = line.wordHighlightMs[currentWord];
            slotEnd   = (currentWord + 1 < line.wordHighlightMs.length)
                        ? line.wordHighlightMs[currentWord + 1]
                        : line.endTimeMs;
        }

        long slotDuration = slotEnd - slotStart;
        long slotElapsed  = currentTimeMs - slotStart;
        double fraction   = slotDuration > 0
                            ? Math.min(1.0, (double) slotElapsed / slotDuration)
                            : 1.0;

        // Calculate wipe X from pixel widths
        int completedPx = 0;
        for (int i = 0; i < currentWord; i++) {
            completedPx += wordWidths[i] + spaceWidth;
        }
        int wipeX    = lineStartX + completedPx + (int)(wordWidths[currentWord] * fraction);
        int clipWidth = wipeX - lineStartX;
        if (clipWidth <= 0) return;

        // Pass 2: cyan clipped to wipe region
        pApplet.clip(lineStartX, y - pApplet.textAscent(), clipWidth, pApplet.textAscent() + pApplet.textDescent());
        pApplet.color(0, 220, 255);
        pApplet.text(fullText, lineStartX, y);
        pApplet.clear();
    }

    private void drawCenteredText(String text, int y, int r, int b, int g) {
    	pApplet.color(r, b, g);
        int x = (width - (int)pApplet.textWidth(text)) / 2;
        pApplet.text(text, x, y);
    }

    public int getWidth()  { return width;  }
    public int getHeight() { return height; }
}
