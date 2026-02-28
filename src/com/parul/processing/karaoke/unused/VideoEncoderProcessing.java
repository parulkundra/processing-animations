package com.parul.processing.karaoke.unused;

import java.io.IOException;
import java.util.List;

public class VideoEncoderProcessing {

    private final String  outputVideoPath;
    private final int     width;
    private final int     height;
    private final double  fps;
    private final long    durationMs;
    private final boolean isAudioOnly;      // true = MP3 input

    /** Print a progress update every N frames */
    private static final int PROGRESS_EVERY = 30;

    public VideoEncoderProcessing(String inputAudioPath, String outputVideoPath,
                        int width, int height, double fps, long durationMs,
                        boolean isAudioOnly) {
        this.outputVideoPath = outputVideoPath;
        this.width           = width;
        this.height          = height;
        this.fps             = fps;
        this.durationMs      = durationMs;
        this.isAudioOnly     = isAudioOnly;
    }

    /**
     * Renders all frames and encodes the output MP4.
     *
     * @param lines    parsed lyric lines
     * @param renderer frame renderer (pre-initialised with correct dimensions)
     */
    public void encode(List<LyricLine> lines, FrameRendererProcessing renderer)
            throws IOException, InterruptedException {

        long totalFrames = (long)(durationMs / 1000.0 * fps);
        System.out.printf("Encoding %d frames at %.2f fps (%dx%d) from %s input…%n",
                          totalFrames, fps, width, height,
                          isAudioOnly ? "MP3" : "MP4");

        double msPerFrame = 1000.0 / fps;

        for (long frame = 0; frame < totalFrames; frame++) {
            long currentTimeMs = (long)(frame * msPerFrame);

            renderer.renderFrame(lines, currentTimeMs);

            if (frame % PROGRESS_EVERY == 0) {
                double pct = 100.0 * frame / totalFrames;
                System.out.printf("\r  Progress: %5.1f%%  (frame %d / %d)",
                                  pct, frame, totalFrames);
                System.out.flush();
            }
        }

        System.out.println("\r  Progress: 100.0%  — waiting for FFmpeg to finish…");

        System.out.println("Encoding complete → " + outputVideoPath);
    }

}
