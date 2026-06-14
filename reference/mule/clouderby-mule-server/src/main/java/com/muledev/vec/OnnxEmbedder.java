package com.muledev.vec;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

import java.io.InputStream;
import java.nio.LongBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Real semantic sentence embedder: multilingual-e5-small (quantized ONNX) run
 * in-JVM via ONNX Runtime, with a pure-Java SentencePiece Unigram tokenizer.
 * Produces 384-dim L2-normalized sentence vectors via attention-masked mean
 * pooling — the recipe sentence-transformers / e5 use. Multilingual, so Japanese
 * works (unlike the English-only all-MiniLM it replaces).
 *
 * Model (~118 MB) and vocab are bundled under /model in the app jar, so the whole
 * embedder ships inside the Mule artifact. ONNX Runtime carries one native lib;
 * all access pins the thread context classloader to this class's loader so it
 * resolves under Mule's isolated app classloader.
 *
 * e5 expects an instruction prefix ("query: " / "passage: ") on the input — the
 * caller (VectorIndex) adds it.
 */
public final class OnnxEmbedder {

    public static final int DIM = 384;
    private static final int MAX_LEN = 256;

    private static volatile OnnxEmbedder instance;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final SentencePieceUnigram tokenizer;
    private final boolean needsTokenTypeIds;

    private OnnxEmbedder() throws Exception {
        try (InputStream model = res("/model/e5-small.onnx");
             InputStream vocab = res("/model/e5_vocab.tsv")) {
            byte[] modelBytes = model.readAllBytes();
            this.env = OrtEnvironment.getEnvironment();
            this.session = env.createSession(modelBytes, new OrtSession.SessionOptions());
            this.tokenizer = new SentencePieceUnigram(vocab, MAX_LEN);
            this.needsTokenTypeIds = session.getInputNames().contains("token_type_ids");
        }
    }

    private static InputStream res(String path) {
        InputStream in = OnnxEmbedder.class.getResourceAsStream(path);
        if (in == null) throw new IllegalStateException("bundled resource not found: " + path);
        return in;
    }

    private static OnnxEmbedder get() throws Exception {
        OnnxEmbedder local = instance;
        if (local == null) {
            synchronized (OnnxEmbedder.class) {
                local = instance;
                if (local == null) {
                    instance = local = new OnnxEmbedder();
                }
            }
        }
        return local;
    }

    /** Embed text into a 384-dim L2-normalized sentence vector. */
    public static float[] embed(String text) throws Exception {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(OnnxEmbedder.class.getClassLoader());
        try {
            return get().run(text == null ? "" : text);
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private synchronized float[] run(String text) throws Exception {
        long[] ids = tokenizer.encode(text);
        int seq = ids.length;
        long[] mask = new long[seq];
        long[] types = new long[seq];
        for (int i = 0; i < seq; i++) mask[i] = 1L; // single sentence, no padding

        long[] shape = {1, seq};
        Map<String, OnnxTensor> inputs = new HashMap<>();
        OnnxTensor tIds = OnnxTensor.createTensor(env, LongBuffer.wrap(ids), shape);
        OnnxTensor tMask = OnnxTensor.createTensor(env, LongBuffer.wrap(mask), shape);
        OnnxTensor tTypes = needsTokenTypeIds
                ? OnnxTensor.createTensor(env, LongBuffer.wrap(types), shape) : null;
        try {
            inputs.put("input_ids", tIds);
            inputs.put("attention_mask", tMask);
            if (tTypes != null) inputs.put("token_type_ids", tTypes);

            try (OrtSession.Result result = session.run(inputs)) {
                // last_hidden_state: [1][seq][DIM]
                float[][][] hidden = (float[][][]) result.get(0).getValue();
                return meanPoolNormalize(hidden[0], mask);
            }
        } finally {
            tIds.close();
            tMask.close();
            if (tTypes != null) tTypes.close();
        }
    }

    /** Attention-masked mean pooling over tokens, then L2 normalize. */
    private static float[] meanPoolNormalize(float[][] hidden, long[] mask) {
        float[] v = new float[DIM];
        double count = 0;
        for (int t = 0; t < hidden.length; t++) {
            if (mask[t] == 0) continue;
            count++;
            float[] h = hidden[t];
            for (int d = 0; d < DIM; d++) v[d] += h[d];
        }
        if (count > 0) for (int d = 0; d < DIM; d++) v[d] /= count;
        double norm = 0;
        for (float x : v) norm += x * x;
        if (norm > 0) {
            double inv = 1.0 / Math.sqrt(norm);
            for (int d = 0; d < DIM; d++) v[d] *= inv;
        }
        return v;
    }
}
