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
 * Pure-Java WordPiece tokenizer for BERT-uncased models (e.g. all-MiniLM-L6-v2).
 *
 * Avoids a second native library (the HF Rust tokenizer) by implementing the
 * BERT preprocessing directly: lowercase + accent strip, basic punctuation
 * splitting, then greedy longest-match WordPiece against the bundled vocab.txt.
 * Sufficient for English sentence embedding; not a full multilingual BERT
 * basic-tokenizer (no CJK char splitting).
 */
public final class WordPiece {

    private final Map<String, Integer> vocab = new HashMap<>();
    private final int clsId, sepId, unkId, padId;
    private final int maxLen;

    public WordPiece(InputStream vocabTxt, int maxLen) throws Exception {
        this.maxLen = maxLen;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(vocabTxt, StandardCharsets.UTF_8))) {
            String line;
            int idx = 0;
            while ((line = r.readLine()) != null) {
                // vocab.txt: one token per line, id == line number
                vocab.put(line, idx++);
            }
        }
        clsId = id("[CLS]");
        sepId = id("[SEP]");
        unkId = id("[UNK]");
        padId = vocab.getOrDefault("[PAD]", 0);
    }

    private int id(String tok) {
        Integer v = vocab.get(tok);
        if (v == null) throw new IllegalStateException("vocab missing special token " + tok);
        return v;
    }

    /** Token ids for a single sentence, wrapped in [CLS] ... [SEP], capped at maxLen. */
    public long[] encode(String text) {
        List<Integer> ids = new ArrayList<>();
        ids.add(clsId);
        for (String basic : basicTokenize(text)) {
            for (int pieceId : wordpiece(basic)) {
                if (ids.size() >= maxLen - 1) break;
                ids.add(pieceId);
            }
        }
        ids.add(sepId);
        long[] out = new long[ids.size()];
        for (int i = 0; i < ids.size(); i++) out[i] = ids.get(i);
        return out;
    }

    public int padId() { return padId; }

    /** Lowercase, strip accents, split on whitespace and punctuation. */
    private List<String> basicTokenize(String text) {
        if (text == null) return List.of();
        String n = Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", ""); // drop combining marks (accent strip)
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (Character.isWhitespace(c)) {
                flush(cur, tokens);
            } else if (isPunct(c)) {
                flush(cur, tokens);
                tokens.add(String.valueOf(c));
            } else {
                cur.append(c);
            }
        }
        flush(cur, tokens);
        return tokens;
    }

    private static void flush(StringBuilder cur, List<String> tokens) {
        if (cur.length() > 0) {
            tokens.add(cur.toString());
            cur.setLength(0);
        }
    }

    private static boolean isPunct(char c) {
        if ((c >= 33 && c <= 47) || (c >= 58 && c <= 64)
            || (c >= 91 && c <= 96) || (c >= 123 && c <= 126)) return true;
        int type = Character.getType(c);
        return type == Character.CONNECTOR_PUNCTUATION || type == Character.DASH_PUNCTUATION
            || type == Character.START_PUNCTUATION || type == Character.END_PUNCTUATION
            || type == Character.INITIAL_QUOTE_PUNCTUATION || type == Character.FINAL_QUOTE_PUNCTUATION
            || type == Character.OTHER_PUNCTUATION;
    }

    /** Greedy longest-match WordPiece for one whitespace token. */
    private List<Integer> wordpiece(String token) {
        List<Integer> ids = new ArrayList<>();
        int len = token.length();
        if (len > 100) { ids.add(unkId); return ids; }
        int start = 0;
        while (start < len) {
            int end = len;
            String matched = null;
            while (start < end) {
                String sub = token.substring(start, end);
                if (start > 0) sub = "##" + sub;
                if (vocab.containsKey(sub)) { matched = sub; break; }
                end--;
            }
            if (matched == null) { ids.clear(); ids.add(unkId); return ids; }
            ids.add(vocab.get(matched));
            start = end;
        }
        return ids;
    }
}
