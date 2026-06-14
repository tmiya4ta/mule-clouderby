# material-search — Vector search over clouderby's manufacturing data

A small, runnable example showing **how a vector index earns its keep on the
materials-maker dataset that clouderby ships by default** — and, just as
importantly, where it stops and SQL (or an LLM) takes over.

It uses only the Python standard library, so it runs with no `pip install`.

## The problem it solves

clouderby's demo data has ~200 products (`MDM_PRODUCTS`), stock
(`INV_INVENTORY`), suppliers, orders, etc. Plain SQL can only match a product by
*exact text*:

```sql
SELECT * FROM MDM_PRODUCTS WHERE PRODUCT_NAME LIKE '%ステンレス%'
```

That breaks the moment a human phrases it differently — "stainless sheet",
"SUS304 の板", "錆びない金属板". A vector index matches by **meaning** instead, so
any of those phrasings resolves to the right rows. Then SQL does the precise
operation (stock, price) on whatever the vector layer resolved.

## The pattern (the whole point)

```
 free text          ──▶  [vector search]   ──▶  concrete PRODUCT_IDs    (fuzzy → entity)
 "epoxy resin"            nearest by meaning      P010, P046, P047
 "樹脂 100円以下"            │
                           │   plus a cheap regex for structured constraints
                           │   ("100円以下" → max_price=100)   ← no LLM needed
                           ▼
                    [clouderby SQL]  ──▶  stock / price / low-stock flag
                    precise operation on the resolved rows
```

- **Vector search** = the bridge from how a human *phrases* a request to the
  exact records. This is the part SQL cannot do.
- **Regex** = pulls structured parameters (price caps, sizes, grades) that are
  regular enough to not need an LLM.
- **SQL** = operates on the resolved rows (here: join to inventory for stock).

No LLM is involved. The vector index collapses infinite phrasings into concrete
`PRODUCT_ID`s; everything downstream is deterministic SQL.

## Run it

```bash
# 1) index the 200 products into clouderby's built-in vector index (one-time;
#    the index is in-memory on the server, so re-run after a server restart)
python3 material_search.py ingest

# 2) search by meaning
python3 material_search.py search "epoxy resin"
python3 material_search.py search "silicon wafer"
python3 material_search.py search "樹脂 100円以下"      # regex pulls the price cap
python3 material_search.py search "copper wire" --max-price 30 -k 5

# 3) a canned tour
python3 material_search.py demo
```

Point it at your own server with `CLOUDERBY_URL`, `CLOUDERBY_USER`,
`CLOUDERBY_PASSWORD`, `CLOUDERBY_DB`.

## Example output

```
query: 'epoxy resin'
----------------------------------------------------------------
エポキシ樹脂 A型             RAW_MATERIAL  ¥65       在庫   1047 (引当 53)
    match score=0.874  id=P010
エポキシ樹脂 B型             RAW_MATERIAL  ¥78       在庫      0 (引当 0)  ⚠ LOW STOCK
    match score=0.874  id=P046

query: '樹脂 100円以下'
constraints (regex-extracted): {'max_price': 100.0}
----------------------------------------------------------------
ABS樹脂ペレット             RAW_MATERIAL  ¥48       在庫      0 (引当 0)  ⚠ LOW STOCK
エポキシ樹脂 A型             RAW_MATERIAL  ¥65       在庫   1047 (引当 53)
```

`copper wire` → 銅線 2.5/1.0/5.0mm, `silicon wafer` → シリコンウェーハ
300/200/150mm, `アルミ 5052` → アルミ合金 5052/7075 — all by meaning, none by
`LIKE`.

## Honest limitations (and how to fix them)

- **The bundled embedding model (all-MiniLM-L6-v2) is English.** It handles
  English, loanwords, and alphanumeric codes well (`epoxy`, `silicon wafer`,
  `SUS304`, `5052`), but **not pure-Japanese semantics** ("錆びない板" won't reach
  ステンレス on its own). This example papers over that with a tiny JP→EN
  gazetteer that enriches both the indexed text and the query before embedding —
  a cheap, honest stand-in. For production Japanese, swap the server's model for
  a multilingual one (e.g. `multilingual-e5-small` / `bge-m3`); the `/vectors`
  contract is unchanged.
- **Brute-ish ingest:** the example upserts one product per HTTP call for
  clarity. A real loader would batch.
- **In-memory index:** the server's vector index rebuilds on restart, so re-run
  `ingest`.

## Where an LLM *would* come in (and where it wouldn't)

This example deliberately uses **no LLM**, to show how much is doable without
one:

- "find me a stainless sheet under ¥500" → vector (stainless) + regex (¥500) +
  SQL (stock) — **no LLM**.
- "発注番号 PO-1023 の樹脂を 200kg 追加で" → the IDs/quantities are regular →
  **regex/parsers**, still no LLM.
- "この前頼んだやつ、やっぱりキャンセル" → ambiguous, context-dependent → **this** is
  where an LLM (or dialog state) is actually needed.
- "なぜ先月ステンレスの在庫が切れたのか説明して" → open-ended Q&A over many records →
  retrieve with vectors, then **an LLM** synthesizes the answer (RAG).

So the vector index is the cheap front door: it turns messy free text into
concrete records that deterministic SQL can act on, and only the genuinely
ambiguous or generative tail needs an LLM.
