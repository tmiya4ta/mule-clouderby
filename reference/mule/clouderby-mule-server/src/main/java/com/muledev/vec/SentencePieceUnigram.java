package com.muledev.vec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure-Java SentencePiece Unigram tokenizer for XLM-RoBERTa-based models
 * (multilingual-e5, paraphrase-multilingual-MiniLM, ...). No native library.
 *
 * Mirrors the reference pipeline:
 *   1. normalize    — NFKC (close approximation of the model's Precompiled charsmap)
 *   2. metaspace    — prepend a leading space (add_prefix_space) and map ' ' -> '▁'
 *   3. unigram      — Viterbi best segmentation maximizing the sum of piece scores
 *   4. post-process — wrap with <s> ... </s>
 *
 * Vocab is a TSV ("<score>\t<piece>", line number == id) extracted from the HF
 * tokenizer.json and bundled under /model. Special tokens live at the front of
 * the vocab (no fairseq offset): &lt;s&gt;=0, &lt;pad&gt;=1, &lt;/s&gt;=2, &lt;unk&gt;=3.
 */
public final class SentencePieceUnigram {

    private static final char META = '▁';        // ▁
    private static final int BOS = 0, EOS = 2, UNK = 3;

    private final Map<String, Integer> pieceToId = new HashMap<>();
    private final double[] scores;
    private final int maxPieceLen;
    private final double unkScore;
    private final int maxLen;

    public SentencePieceUnigram(InputStream vocabTsv, int maxLen) throws Exception {
        this.maxLen = maxLen;
        List<Double> sc = new ArrayList<>(260000);
        int longest = 1;
        double minScore = 0.0;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(vocabTsv, StandardCharsets.UTF_8))) {
            String line;
            int id = 0;
            while ((line = r.readLine()) != null) {
                int tab = line.indexOf('\t');
                double score = Double.parseDouble(line.substring(0, tab));
                String piece = line.substring(tab + 1);
                pieceToId.put(piece, id);
                sc.add(score);
                if (piece.length() > longest) longest = piece.length();
                if (score < minScore) minScore = score;
                id++;
            }
        }
        this.scores = new double[sc.size()];
        for (int i = 0; i < scores.length; i++) scores[i] = sc.get(i);
        this.maxPieceLen = Math.min(longest, 48);     // bound the Viterbi inner loop
        this.unkScore = minScore - 10.0;
    }

    /** Token ids for a single sentence, wrapped in &lt;s&gt; ... &lt;/s&gt;, capped at maxLen. */
    public long[] encode(String text) {
        String norm = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC);
        norm = norm.replaceAll("\\s", " ");           // tabs/newlines/etc. -> space (like SP)
        int[] ids = viterbi(metaspace(norm));

        int n = Math.min(ids.length, maxLen - 2);
        long[] out = new long[n + 2];
        out[0] = BOS;
        for (int i = 0; i < n; i++) out[i + 1] = ids[i];
        out[n + 1] = EOS;
        return out;
    }

    /** add_prefix_space + replace spaces with the metaspace marker. */
    private static String metaspace(String n) {
        if (n.isEmpty() || n.charAt(0) != ' ') {
            n = " " + n;                              // add_prefix_space (only if not already)
        }
        return n.replace(' ', META);
    }

    /** Viterbi over code units: maximize summed piece scores; unknown chars -> &lt;unk&gt;. */
    private int[] viterbi(String s) {
        int len = s.length();
        double[] best = new double[len + 1];
        int[] backPos = new int[len + 1];
        int[] backId = new int[len + 1];
        java.util.Arrays.fill(best, Double.NEGATIVE_INFINITY);
        best[0] = 0.0;

        for (int i = 0; i < len; i++) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue;
            int max = Math.min(maxPieceLen, len - i);
            boolean matched = false;
            for (int L = max; L >= 1; L--) {
                Integer id = pieceToId.get(s.substring(i, i + L));
                if (id == null) continue;
                double cand = best[i] + scores[id];
                if (cand > best[i + L]) {
                    best[i + L] = cand; backPos[i + L] = i; backId[i + L] = id;
                }
                matched = true;
            }
            // unknown fallback keeps the lattice connected; advance a full code
            // point so a surrogate pair (e.g. an emoji) becomes a single <unk>.
            int step = (Character.isHighSurrogate(s.charAt(i)) && i + 1 < len
                        && Character.isLowSurrogate(s.charAt(i + 1))) ? 2 : 1;
            double cand = best[i] + unkScore;
            if (cand > best[i + step]) {
                best[i + step] = cand; backPos[i + step] = i; backId[i + step] = UNK;
            }
            if (!matched) { /* only the unk edge advances from i */ }
        }

        ArrayList<Integer> rev = new ArrayList<>();
        int p = len;
        while (p > 0) {
            rev.add(backId[p]);
            p = backPos[p];
        }
        int[] ids = new int[rev.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = rev.get(ids.length - 1 - i);
        return ids;
    }
}
