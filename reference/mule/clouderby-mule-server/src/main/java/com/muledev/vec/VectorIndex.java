package com.muledev.vec;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

/**
 * In-JVM ANN vector index for clouderby, backed by Lucene HNSW (pure Java, no
 * native deps). This replaces the Derby-UDF brute-force O(n) scan with a real
 * sublinear approximate-nearest-neighbour index, bundled inside the Mule app.
 *
 * The KNN search is not expressible as Derby SQL, so it is reached through
 * dedicated HTTP endpoints (POST /vectors/upsert, /vectors/search, /vectors/clear)
 * rather than the SQL path. Text is embedded server-side with {@link OnnxEmbedder}
 * (MiniLM), so callers pass plain strings.
 *
 * In-memory index (ByteBuffersDirectory): CloudHub's filesystem is ephemeral, so
 * persistence buys nothing here; the index rebuilds on restart from whatever is
 * re-ingested. All operations run with the thread context classloader pinned to
 * this class's loader so Lucene's Codec SPI resolves under Mule's isolated
 * app classloader.
 */
public final class VectorIndex {

    private static final String F_ID = "id";
    private static final String F_CONTENT = "content";
    private static final String F_VECTOR = "vector";

    private static volatile Directory directory;
    private static volatile IndexWriter writer;

    private VectorIndex() {}

    private static synchronized IndexWriter writer() throws Exception {
        if (writer == null) {
            directory = new ByteBuffersDirectory();
            writer = new IndexWriter(directory, new IndexWriterConfig());
            writer.commit(); // create an empty, openable index
        }
        return writer;
    }

    /** Index (or replace) a document: embed content, store id+content+vector. */
    public static String upsert(String id, String content) throws Exception {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(VectorIndex.class.getClassLoader());
        try {
            IndexWriter w = writer();
            float[] vec = OnnxEmbedder.embed("passage: " + content);   // e5 doc prefix
            Document doc = new Document();
            doc.add(new StringField(F_ID, id, Field.Store.YES));
            doc.add(new StoredField(F_CONTENT, content));
            doc.add(new KnnFloatVectorField(F_VECTOR, vec, VectorSimilarityFunction.COSINE));
            w.updateDocument(new Term(F_ID, id), doc); // upsert by id
            w.commit();
            return "{\"indexed\":true,\"id\":" + jsonStr(id) + ",\"dim\":" + OnnxEmbedder.DIM + "}";
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /** ANN search: embed the query text, return top-k {id, content, score} as JSON. */
    public static String search(String query, int k) throws Exception {
        if (k <= 0) k = 5;
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(VectorIndex.class.getClassLoader());
        try {
            IndexWriter w = writer();
            float[] qv = OnnxEmbedder.embed("query: " + query);        // e5 query prefix
            try (DirectoryReader reader = DirectoryReader.open(w)) {
                IndexSearcher searcher = new IndexSearcher(reader);
                KnnFloatVectorQuery q = new KnnFloatVectorQuery(F_VECTOR, qv, k);
                long t0 = System.nanoTime();
                TopDocs td = searcher.search(q, k);
                long micros = (System.nanoTime() - t0) / 1000;

                var sf = searcher.storedFields();
                StringBuilder sb = new StringBuilder();
                sb.append("{\"query\":").append(jsonStr(query))
                  .append(",\"k\":").append(k)
                  .append(",\"count\":").append(reader.numDocs())
                  .append(",\"searchMicros\":").append(micros)
                  .append(",\"hits\":[");
                for (int i = 0; i < td.scoreDocs.length; i++) {
                    ScoreDoc sd = td.scoreDocs[i];
                    Document d = sf.document(sd.doc);
                    if (i > 0) sb.append(',');
                    sb.append("{\"id\":").append(jsonStr(d.get(F_ID)))
                      .append(",\"content\":").append(jsonStr(d.get(F_CONTENT)))
                      .append(",\"score\":").append(sd.score)
                      .append('}');
                }
                sb.append("]}");
                return sb.toString();
            }
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    /** Drop and recreate the index. */
    public static synchronized String clear() throws Exception {
        ClassLoader prev = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(VectorIndex.class.getClassLoader());
        try {
            if (writer != null) {
                writer.close();
                writer = null;
            }
            if (directory != null) {
                directory.close();
                directory = null;
            }
            writer(); // recreate empty
            return "{\"cleared\":true}";
        } finally {
            Thread.currentThread().setContextClassLoader(prev);
        }
    }

    private static String jsonStr(String s) {
        if (s == null) return "null";
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
