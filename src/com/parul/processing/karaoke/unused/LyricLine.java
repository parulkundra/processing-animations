package com.parul.processing.karaoke.unused;

/**
 * Represents a single line of lyrics.
 *
 * Supports three timing modes:
 *
 *   MODE 1 - Explicit end times (highest accuracy):
 *     Each word has both wordStartMs[i] and wordEndMs[i] from the file.
 *     Format: [start]word[end]_[start]nextWord[end]_
 *
 *   MODE 2 - Per-word start times only:
 *     wordHighlightMs[i] from file, end time = start of next word.
 *     Format: [start]word [start]word
 *
 *   MODE 3 - Equal distribution (fallback):
 *     Single line-level timestamp, timings evenly distributed.
 */
public class LyricLine {

    /** Start time of this line in milliseconds (timestamp of first word) */
    public final long startTimeMs;

    /** End time of this line in milliseconds (= next line's startTime, or explicit) */
    public long endTimeMs;

    /** Individual words in display order */
    public final String[] words;

    /**
     * Per-word wipe start times in milliseconds.
     * wordHighlightMs[i] = exact time when the cyan wipe starts on word[i].
     */
    public long[] wordHighlightMs;

    /**
     * Per-word explicit end times in milliseconds (MODE 1 only).
     * wordEndMs[i] = exact time when word[i] ends.
     * Null for MODE 2 and MODE 3.
     */
    public long[] wordEndMs;

    /** True if word timings came from per-word timestamps in the file */
    public final boolean hasPerWordTimings;

    /** True if each word has explicit start AND end times (MODE 1) */
    public final boolean hasExplicitEndTimes;

    /**
     * Constructor for MODE 1: explicit start and end times for each word.
     *
     * @param words        array of words in the line
     * @param wordStartMs  array of start timestamps, one per word (ms)
     * @param wordEndMs    array of end timestamps, one per word (ms)
     */
    public LyricLine(String[] words, long[] wordStartMs, long[] wordEndMs) {
        this.words               = words;
        this.wordHighlightMs     = wordStartMs; // start times
        this.wordEndMs           = wordEndMs;   // end times
        this.startTimeMs         = wordStartMs[0];
        this.endTimeMs           = wordEndMs[wordEndMs.length - 1]; // last word's end
        this.hasPerWordTimings   = true;
        this.hasExplicitEndTimes = true;
    }

    /**
     * Constructor for MODE 2: per-word start times only.
     * End time for each word = start of next word (calculated by FrameRenderer).
     *
     * @param words            array of words in the line
     * @param wordHighlightMs  array of start timestamps, one per word (ms)
     */
    public LyricLine(String[] words, long[] wordHighlightMs) {
        this.words               = words;
        this.wordHighlightMs     = wordHighlightMs;
        this.wordEndMs           = null; // not used in this mode
        this.startTimeMs         = wordHighlightMs[0];
        this.endTimeMs           = startTimeMs; // set later by parser
        this.hasPerWordTimings   = true;
        this.hasExplicitEndTimes = false;
    }

    /**
     * Constructor for MODE 3: only line-level timestamp provided.
     * Word timings will be evenly distributed once endTimeMs is known.
     *
     * @param startTimeMs  when this line starts
     * @param text         full line text (words split automatically)
     */
    public LyricLine(long startTimeMs, String text) {
        this.startTimeMs         = startTimeMs;
        this.words               = text.trim().split("\\s+");
        this.wordHighlightMs     = new long[words.length];
        this.wordEndMs           = null;
        this.endTimeMs           = startTimeMs;
        this.hasPerWordTimings   = false;
        this.hasExplicitEndTimes = false;
    }

    /**
     * Reconstructs the full display text from the words array.
     */
    public String getText() {
        return String.join(" ", words);
    }

    /**
     * Called after endTimeMs is set (for MODE 2 and MODE 3 only).
     * MODE 1: nothing to calculate - explicit end times already set.
     * MODE 2: per-word start times already set - endTimeMs defines last word duration.
     * MODE 3: evenly distributes timing across all words.
     */
    public void finaliseTimings() {
        if (hasExplicitEndTimes) {
            // MODE 1: explicit end times already set, nothing to do
            return;
        }
        if (!hasPerWordTimings) {
            // MODE 3: equal distribution fallback
            if (words.length == 0) return;
            long duration = endTimeMs - startTimeMs;
            long perWord  = duration / words.length;
            for (int i = 0; i < words.length; i++) {
                wordHighlightMs[i] = startTimeMs + (i * perWord);
            }
        }
        // MODE 2: per-word start times already set from file
    }

    /**
     * Returns smooth wipe progress from 0.0 to 1.0 across the whole line.
     *
     * For MODE 1 (explicit end times):
     *   Each word's slot spans from wordStartMs[i] to wordEndMs[i].
     *
     * For MODE 2 (per-word start times):
     *   Each word's slot spans from wordHighlightMs[i] to wordHighlightMs[i+1]
     *   (last word spans to endTimeMs).
     *
     * For MODE 3 (equal distribution):
     *   Same as MODE 2 after finaliseTimings() calculates the slots.
     *
     * Within each slot, progress moves smoothly 0.0 to 1.0.
     * Overall progress = (completedWords + slotFraction) / totalWords
     */
    public double getWipeProgress(long currentTimeMs) {
        if (currentTimeMs <= startTimeMs) return 0.0;
        if (currentTimeMs >= endTimeMs)   return 1.0;
        if (words.length == 0)            return 0.0;

        // Single word: simple linear progress
        if (words.length == 1) {
            long duration = hasExplicitEndTimes
                            ? (wordEndMs[0] - wordHighlightMs[0])
                            : (endTimeMs - startTimeMs);
            long elapsed  = currentTimeMs - startTimeMs;
            return duration > 0 ? (double) elapsed / duration : 1.0;
        }

        // Find which word slot we are currently in
        int currentWord = 0;
        for (int i = wordHighlightMs.length - 1; i >= 0; i--) {
            if (currentTimeMs >= wordHighlightMs[i]) {
                currentWord = i;
                break;
            }
        }

        // Calculate slot start and end times
        long slotStart, slotEnd;
        if (hasExplicitEndTimes) {
            // MODE 1: explicit end times per word
            slotStart = wordHighlightMs[currentWord];
            slotEnd   = wordEndMs[currentWord];
        } else {
            // MODE 2 or MODE 3: end = start of next word (or line end)
            slotStart = wordHighlightMs[currentWord];
            slotEnd   = (currentWord + 1 < wordHighlightMs.length)
                        ? wordHighlightMs[currentWord + 1]
                        : endTimeMs;
        }

        long slotDuration = slotEnd - slotStart;
        long slotElapsed  = currentTimeMs - slotStart;

        double slotFraction = slotDuration > 0
                              ? (double) slotElapsed / slotDuration
                              : 1.0;
        slotFraction = Math.min(1.0, Math.max(0.0, slotFraction));

        // Map to overall line progress
        return (currentWord + slotFraction) / words.length;
    }

    @Override
    public String toString() {
        String modeLabel = hasExplicitEndTimes ? "(explicit-end)"
                         : hasPerWordTimings   ? "(per-word-start)"
                         : "(equal-dist)";
        return String.format("[%d ms] %s %s", startTimeMs, getText(), modeLabel);
    }
}
