//package com.parul.processing.karaoke.unused;
//
//import java.io.File;
//import java.util.List;
//
//import processing.core.PApplet;
//import processing.core.PFont;
//
///**
// * ╔══════════════════════════════════════════════════════════════╗
// * ║              K A R A O K E   R U N N E R                     ║
// * ╚══════════════════════════════════════════════════════════════╝
// * <p>
// * Two modes:
// * <p>
// * MODE 1 — Karaoke only:
// * java -cp out karaoke.KaraokeGenerator <song> <lyrics> <output.mp4>
// * <p>
// * MODE 2 — Full production (intro + text screen + karaoke):
// * java -cp out karaoke.KaraokeGenerator <song> <lyrics> <output.mp4>
// * --intro <intro.mp4>
// * --text  <songinfo.html> <seconds>
// * <p>
// * Examples:
// * java -cp out karaoke.KaraokeGenerator song.mp3 pankuLyrics.txt final.mp4
// * <p>
// * java -cp out karaoke.KaraokeGenerator song.mp3 pankuLyrics.txt final.mp4
// * --intro myintro.mp4
// * --text songinfo.html 3
// * <p>
// * Text HTML file format (songinfo.html):
// * <bg:#1a1a2e>
// * <color:#00DCFF><size:52><b>Tum Hi Ho
// * <color:white><size:36>Singer: Arijit Singh
// * <color:gray><size:28>Movie: Aashiqui 2 (2013)
// * <p>
// * Supported tags: <b> <i> <size:N> <color:#RRGGBB> <color:name> <bg:#RRGGBB>
// * Named colours: red white black cyan yellow green blue pink orange gray gold purple
// */
//public class KaraokeRunnerProcessing {
//	
//    public void run(PApplet pApplet, String folder, String song, String lyrics, String output, String key, String introPath, String textHtmlPath, PFont font, Integer linesPerVerse) {
//        String inputFile = folder + key + song;
//        String lyricsFile = folder + key + lyrics;
//        String outputFile = folder + key + output;
//        boolean isFastSong = true;
//
//        // ── Parse optional flags ───────────────────────────────────────────────
//        float textDuration = 3.0f;
//
//        boolean withIntro = introPath != null;
//        boolean withText = textHtmlPath != null;
//        boolean fullMode = withIntro || withText;
//
//        // ── Validate input file extension ──────────────────────────────────────
//        String ext = inputFile.toLowerCase();
//        if (!ext.endsWith(".mp3") && !ext.endsWith(".mp4")
//                && !ext.endsWith(".mov") && !ext.endsWith(".mkv")
//                && !ext.endsWith(".avi")) {
//            System.err.println("ERROR: Unsupported input format: " + inputFile);
//            System.err.println("Supported: .mp3 .mp4 .mov .mkv .avi");
//            System.exit(1);
//        }
//
//        // ── Print header ───────────────────────────────────────────────────────
//        System.out.println("╔══════════════════════════════════════╗");
//        System.out.println("║      Karaoke Runner v4.0          ║");
//        System.out.println("╚══════════════════════════════════════╝");
//        System.out.println("Song file    : " + inputFile);
//        System.out.println("Lyrics file  : " + lyricsFile);
//        System.out.println("Output       : " + outputFile);
//        if (withIntro) System.out.println("Intro MP4    : " + introPath);
//        if (withText) System.out.println("Text screen  : " + textHtmlPath + " (" + textDuration + "s)");
//        System.out.println();
//
//        // ── Temp file paths ────────────────────────────────────────────────────
//        String textTempPath = outputFile + "_text_temp.mp4";
//        String karaokeTempPath = fullMode ? outputFile + "_karaoke_temp.mp4"
//                : outputFile;
//
//        try {
//            // ── Step 1: Probe input song ───────────────────────────────────────
//            System.out.println("Step 1: Probing input file…");
//            VideoProbe probe = VideoProbe.probe(inputFile);
//            boolean isAudioOnly = probe.isAudioOnly;
//            if (isAudioOnly) {
//                System.out.printf("  Audio-only input → output %dx%d @ %.0f fps%n",
//                        probe.width, probe.height, probe.fps);
//            }
//
//            // ── Step 2: Parse lyrics ───────────────────────────────────────────
//            System.out.println("Step 2: Parsing lyrics…");
//            List<LyricLine> lines = LRCParser.parse(lyricsFile, probe.durationMs);
//            if (lines.isEmpty()) {
//                System.err.println("ERROR: No lyric lines parsed from: " + lyricsFile);
//                System.err.println("Format: [00:05.00]Word [00:05.40]by [00:05.80]word");
//                System.exit(1);
//            }
//            printLyricSummary(lines);
//
//            // ── Step 3: Render karaoke video ───────────────────────────────────
//            System.out.println("Step 3: Rendering karaoke video…");
//           
//            FrameRendererProcessing renderer = new FrameRendererProcessing(pApplet, probe.width, probe.height, isFastSong, font, linesPerVerse);
//            VideoEncoderProcessing encoder = new VideoEncoderProcessing(
//                    inputFile, karaokeTempPath,
//                    probe.width, probe.height,
//                    probe.fps, probe.durationMs, isAudioOnly
//            );
//            encoder.encode(lines, renderer);
//            System.out.println();
//
//            // ── Steps 4-6: Full production mode ───────────────────────────────
//            if (fullMode) {
//
//                // Step 4: Encode text screen (if provided)
//                if (withText) {
//                    validateFile(textHtmlPath, "Text HTML file");
//                    System.out.println("Step 4: Encoding text screen…");
//
//                    // Use intro resolution if available, otherwise 1920x1080
//                    int tsWidth = VideoStitcher.TARGET_WIDTH;
//                    int tsHeight = VideoStitcher.TARGET_HEIGHT;
//                    if (withIntro) {
//                        VideoProbe introprobe = VideoProbe.probe(introPath);
//                        if (!introprobe.isAudioOnly) {
//                            tsWidth = introprobe.width;
//                            tsHeight = introprobe.height;
//                        }
//                    }
//
//                    TextScreenRenderer tsRenderer = new TextScreenRenderer(
//                            textHtmlPath, tsWidth, tsHeight,
//                            textDuration, VideoStitcher.TARGET_FPS
//                    );
//                    VideoStitcher.encodeTextScreen(tsRenderer, textTempPath);
//                    System.out.println();
//                }
//
//                // Step 5: Stitch all parts
//                System.out.println("Step 5: Stitching parts…");
//                String effectiveIntro = withIntro ? introPath : null;
//                String effectiveText = withText ? textTempPath : null;
//
//                if (effectiveIntro != null && effectiveText != null) {
//                    // All three parts
//                    VideoStitcher.stitchThreeParts(
//                            effectiveIntro, effectiveText, karaokeTempPath, outputFile);
//                } else if (effectiveIntro != null) {
//                    // Intro + karaoke only
//                    VideoStitcher.stitchThreeParts(
//                            effectiveIntro, null, karaokeTempPath, outputFile);
//                } else {
//                    // Text + karaoke only
//                    VideoStitcher.stitchThreeParts(
//                            null, effectiveText, karaokeTempPath, outputFile);
//                }
//                System.out.println();
//
//                // Step 6: Clean up
//                System.out.println("Step 6: Cleaning up…");
//                deleteIfExists(textTempPath);
//                deleteIfExists(karaokeTempPath);
//            }
//
//            System.out.println();
//            System.out.println("✓ Done! Final video saved to: " + outputFile);
//
//        } catch (Exception e) {
//            System.err.println("\nERROR: " + e.getMessage());
//            e.printStackTrace();
//            deleteIfExists(textTempPath);
//            if (fullMode) deleteIfExists(karaokeTempPath);
//            System.exit(1);
//        }
//    }
//
//    // ── Helpers ────────────────────────────────────────────────────────────────
//
//    private static void validateFile(String path, String label) {
//        if (path == null || !new File(path).exists()) {
//            System.err.println("ERROR: " + label + " not found: " + path);
//            System.exit(1);
//        }
//    }
//
//    private static void deleteIfExists(String path) {
//        if (path == null) return;
//        File f = new File(path);
//        if (f.exists()) {
//            f.delete();
//            System.out.println("  Deleted: " + path);
//        }
//    }
//
//    private static void printLyricSummary(List<LyricLine> lines) {
//        System.out.printf("  %d lines across %d verses%n",
//                lines.size(), (int) Math.ceil(lines.size() / 4.0));
//        int preview = Math.min(3, lines.size());
//        for (int i = 0; i < preview; i++) {
//            System.out.printf("    [%5d ms] %s%n",
//                    lines.get(i).startTimeMs, lines.get(i).getText());
//        }
//        System.out.println();
//    }
//
//    private static void printUsage() {
//        System.out.println("Usage:");
//        System.out.println();
//        System.out.println("  Karaoke only:");
//        System.out.println("    java -cp out karaoke.KaraokeGenerator <song> <lyrics> <output.mp4>");
//        System.out.println();
//        System.out.println("  Full production:");
//        System.out.println("    java -cp out karaoke.KaraokeGenerator <song> <lyrics> <output.mp4>");
//        System.out.println("         --intro <intro.mp4>");
//        System.out.println("         --text  <songinfo.html> <seconds>");
//        System.out.println();
//        System.out.println("  Examples:");
//        System.out.println("    java -cp out karaoke.KaraokeGenerator song.mp3 pankuLyrics.txt out.mp4");
//        System.out.println("    java -cp out karaoke.KaraokeGenerator song.mp3 pankuLyrics.txt out.mp4 --intro intro.mp4 --text info.html 3");
//        System.out.println();
//        System.out.println("  Text HTML tags: <b> <i> <size:N> <color:#RRGGBB> <color:name> <bg:#RRGGBB>");
//        System.out.println("  Colour names: red white black cyan yellow green blue pink orange gray gold purple");
//    }
//}
