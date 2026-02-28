package com.parul.processing.karaoke.unused;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Parses a lyrics file supporting three formats:
 *
 * FORMAT A - Per-word with explicit end times (highest accuracy):
 *   [startTime]word[endTime]SEPARATOR[startTime]nextWord[endTime]SEPARATOR
 *   SEPARATOR can be underscore (_) or space
 *   Examples:
 *     [00:59.03]Hum[00:59.05]_[00:59.03]uske[01:01.08]_
 *     [00:37.32]Chale[00:37.72] [00:37.74]to[00:38.16]
 *
 * FORMAT B - Per-word start times only:
 *   [00:05.00]Is [00:05.40]this [00:05.80]the
 *   Each word has a start time; end time = start of next word.
 *
 * FORMAT C - Line-level timestamp only:
 *   [00:05.00] Is this the real life?
 *   Word timings evenly distributed.
 *
 * All three formats can be mixed in the same file.
 * The parser auto-detects which format each line uses.
 */
public class LRCParser {

    // Matches [MM:SS.cs] or [MM:SS] timestamp
    private static final Pattern TIMESTAMP_PATTERN =
        Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,2}))?\\]");

    // Matches metadata lines like [ti:Title], [ar:Artist]
    private static final Pattern METADATA_PATTERN =
        Pattern.compile("^\\[[a-zA-Z]+:.*\\]\\s*$");

    /**
     * Parses the lyrics file and returns an ordered list of LyricLine objects.
     */
    public static List<LyricLine> parse(String filePath, long songDurationMs) throws IOException {
        List<LyricLine> lines = new ArrayList<>();
        List<String> rawLines = Files.readAllLines(Paths.get(filePath));

        for (String raw : rawLines) {
            raw = raw.trim();
            if (raw.isEmpty()) continue;
            if (METADATA_PATTERN.matcher(raw).matches()) continue;

            LyricLine line = parseLine(raw);
            if (line != null) lines.add(line);
        }

        // Sort by start time
        lines.sort(Comparator.comparingLong(l -> l.startTimeMs));

        // Set endTimeMs for each line = start of next line
        // (except for lines with explicit end times)
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!lines.get(i).hasExplicitEndTimes) {
                lines.get(i).endTimeMs = lines.get(i + 1).startTimeMs;
            }
        }
        if (!lines.isEmpty()) {
            LyricLine last = lines.get(lines.size() - 1);
            if (!last.hasExplicitEndTimes) {
                last.endTimeMs = songDurationMs;
            }
        }

        // Finalise word timings
        for (LyricLine line : lines) {
            line.finaliseTimings();
        }

        // Print summary
        long explicitEnd = lines.stream().filter(l ->  l.hasExplicitEndTimes).count();
        long perWord     = lines.stream().filter(l ->  l.hasPerWordTimings && !l.hasExplicitEndTimes).count();
        long equalDist   = lines.stream().filter(l -> !l.hasPerWordTimings && !l.hasExplicitEndTimes).count();
        System.out.printf("Parsed %d lyric lines: %d explicit-end, %d per-word-start, %d equal-dist%n",
                          lines.size(), explicitEnd, perWord, equalDist);
        return lines;
    }

    // ── Core parsing ───────────────────────────────────────────────────────────

    /**
     * Parses a single line — auto-detects format and returns appropriate LyricLine.
     */
    private static LyricLine parseLine(String raw) {
        // Tokenize: extract all [timestamp] tokens and text between them
        List<Token> tokens = tokenize(raw);
        if (tokens.isEmpty()) return null;

        // Count timestamps
        long tsCount = tokens.stream().filter(t -> t.isTimestamp).count();
        if (tsCount == 0) return null;

        if (tsCount == 1) {
            // FORMAT C: single line-level timestamp
            return parseLineLevelTokens(tokens);
        }

        // Multiple timestamps — could be FORMAT A or B
        // Check if we have alternating pattern: [ts]word[ts] [ts]word[ts]
        // FORMAT A signature: timestamp followed by text followed by timestamp
        if (hasExplicitEndTimesPattern(tokens)) {
            return parseExplicitEndTimesTokens(tokens);
        } else {
            return parsePerWordStartTimesTokens(tokens);
        }
    }

    // ── Tokenizer ──────────────────────────────────────────────────────────────

    private static class Token {
        boolean isTimestamp;
        long timeMs;       // if timestamp
        String text;       // if text
        Token(long timeMs) { this.isTimestamp = true; this.timeMs = timeMs; }
        Token(String text) { this.isTimestamp = false; this.text = text; }
    }

    /**
     * Tokenizes raw line into [timestamp] and text tokens.
     * Example: "[00:05]Is [00:06]this" → [TS:5000, TEXT:"Is ", TS:6000, TEXT:"this"]
     */
    private static List<Token> tokenize(String raw) {
        List<Token> tokens = new ArrayList<>();
        Matcher m = TIMESTAMP_PATTERN.matcher(raw);

        int lastEnd = 0;
        while (m.find()) {
            // Text before this timestamp
            if (m.start() > lastEnd) {
                String text = raw.substring(lastEnd, m.start());
                if (!text.trim().isEmpty()) {
                    tokens.add(new Token(text));
                }
            }

            // The timestamp itself
            long timeMs = parseTimestamp(m.group(1), m.group(2), m.group(3));
            tokens.add(new Token(timeMs));

            lastEnd = m.end();
        }

        // Trailing text after last timestamp
        if (lastEnd < raw.length()) {
            String text = raw.substring(lastEnd);
            if (!text.trim().isEmpty()) {
                tokens.add(new Token(text));
            }
        }

        return tokens;
    }

    // ── Format detection ───────────────────────────────────────────────────────

    /**
     * Returns true if tokens follow FORMAT A pattern:
     *   [ts] text [ts] ... [ts] text [ts]
     * Key signature: timestamp followed by non-whitespace text followed by timestamp.
     */
    private static boolean hasExplicitEndTimesPattern(List<Token> tokens) {
        if (tokens.size() < 3) return false; // need at least [ts]word[ts]

        for (int i = 0; i < tokens.size() - 2; i++) {
            if (tokens.get(i).isTimestamp &&
                !tokens.get(i + 1).isTimestamp &&
                tokens.get(i + 2).isTimestamp) {
                // Found [ts] text [ts] pattern
                String text = tokens.get(i + 1).text.trim();
                // Text must be a single word (no spaces) for explicit end times
                if (!text.isEmpty() && !text.contains(" ")) {
                    return true;
                }
            }
        }
        return false;
    }

    // ── FORMAT A: explicit end times ───────────────────────────────────────────

    /**
     * Parses FORMAT A from tokens: [ts]word[ts] [ts]word[ts]
     * Pattern: each word is bracketed by two timestamps (start and end).
     */
    private static LyricLine parseExplicitEndTimesTokens(List<Token> tokens) {
        List<String> words      = new ArrayList<>();
        List<Long>   startTimes = new ArrayList<>();
        List<Long>   endTimes   = new ArrayList<>();

        for (int i = 0; i < tokens.size() - 2; i++) {
            if (tokens.get(i).isTimestamp &&
                !tokens.get(i + 1).isTimestamp &&
                tokens.get(i + 2).isTimestamp) {

                String wordText = tokens.get(i + 1).text.trim();
                if (!wordText.isEmpty()) {
                    words.add(wordText);
                    startTimes.add(tokens.get(i).timeMs);
                    endTimes.add(tokens.get(i + 2).timeMs);
                }
            }
        }

        if (words.isEmpty()) return null;

        return new LyricLine(
            words.toArray(new String[0]),
            startTimes.stream().mapToLong(Long::longValue).toArray(),
            endTimes.stream().mapToLong(Long::longValue).toArray()
        );
    }

    // ── FORMAT B: per-word start times ─────────────────────────────────────────

    /**
     * Parses FORMAT B from tokens: [ts]word [ts]word [ts]word
     * Each timestamp marks the start of the word(s) that follow it.
     */
    private static LyricLine parsePerWordStartTimesTokens(List<Token> tokens) {
        List<String> words   = new ArrayList<>();
        List<Long>   timings = new ArrayList<>();

        Long currentTime = null;
        for (Token tok : tokens) {
            if (tok.isTimestamp) {
                currentTime = tok.timeMs;
            } else if (currentTime != null) {
                // Text after a timestamp
                for (String word : tok.text.trim().split("\\s+")) {
                    if (!word.isEmpty()) {
                        words.add(word);
                        timings.add(currentTime);
                    }
                }
            }
        }

        if (words.isEmpty()) return null;

        return new LyricLine(
            words.toArray(new String[0]),
            timings.stream().mapToLong(Long::longValue).toArray()
        );
    }

    // ── FORMAT C: line-level timestamp ─────────────────────────────────────────

    /**
     * Parses FORMAT C from tokens: [ts] full line text
     * Single timestamp at start; word timings evenly distributed later.
     */
    private static LyricLine parseLineLevelTokens(List<Token> tokens) {
        if (tokens.isEmpty() || !tokens.get(0).isTimestamp) return null;

        long timeMs = tokens.get(0).timeMs;
        StringBuilder text = new StringBuilder();
        for (int i = 1; i < tokens.size(); i++) {
            if (!tokens.get(i).isTimestamp) {
                text.append(tokens.get(i).text);
            }
        }

        String fullText = text.toString().trim();
        if (fullText.isEmpty()) return null;

        return new LyricLine(timeMs, fullText);
    }

    // ── Timestamp parsing ──────────────────────────────────────────────────────

    /**
     * Converts [MM:SS.cs] to milliseconds.
     */
    private static long parseTimestamp(String minStr, String secStr, String centisStr) {
        int minutes = Integer.parseInt(minStr);
        int seconds = Integer.parseInt(secStr);
        int centis  = 0;
        if (centisStr != null) {
            centis = Integer.parseInt(centisStr);
            if (centisStr.length() == 1) centis *= 10;
        }
        return (minutes * 60L + seconds) * 1000L + centis * 10L;
    }

    // ── Active line lookup ─────────────────────────────────────────────────────

    /**
     * Finds the currently active line index for a given playback time.
     */
    public static int getActiveLineIndex(List<LyricLine> lines, long currentTimeMs) {
        int active = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (currentTimeMs >= lines.get(i).startTimeMs) {
                active = i;
            } else {
                break;
            }
        }
        return active;
    }
}
