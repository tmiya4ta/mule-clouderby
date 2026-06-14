#!/usr/bin/env python3
"""
material_search.py — Semantic material/product search over clouderby's
manufacturing data, using the server's built-in vector index.

What this demonstrates
----------------------
clouderby ships with a small ERP-ish dataset for a materials maker
(MDM_PRODUCTS, INV_INVENTORY, suppliers, orders ...). Plain SQL can only match
products by exact text (`WHERE PRODUCT_NAME LIKE '%...%'`). A vector index lets
you find products by *meaning* — then SQL does the precise operation (stock,
price) on whatever the vector layer resolved.

The pattern (the whole point):

    free text  ──▶ [vector search]  ──▶ concrete PRODUCT_IDs   (fuzzy → entity)
    "ステンレスの板"        nearest by meaning      P003, P045 ...
                                   │
                                   ▼
                          [clouderby SQL]  ──▶ stock / price / reorder
                          precise operation on the resolved rows

Vector search is the bridge from how a human phrases a request to the exact
records; SQL then operates on those records. Optionally a cheap regex pulls
structured constraints (a price cap, a thickness) so you don't need an LLM for
the easy parts — only the messy tail would.

Usage
-----
    python material_search.py ingest
    python material_search.py search "ステンレスの薄い板"
    python material_search.py search "epoxy resin" --max-price 1000 -k 5
    python material_search.py demo

Only the Python standard library is used, so it runs with no pip install.
Set CLOUDERBY_URL / CLOUDERBY_USER / CLOUDERBY_PASSWORD to override defaults.
"""

import json
import os
import re
import sys
import urllib.request

BASE = os.environ.get("CLOUDERBY_URL", "https://mule-clouderby-zsl67m.pnwfdv.jpn-e1.cloudhub.io")
USER = os.environ.get("CLOUDERBY_USER", "mule")
PASSWORD = os.environ.get("CLOUDERBY_PASSWORD", "mule123")
DATABASE = os.environ.get("CLOUDERBY_DB", "app")

# A tiny JP→EN gazetteer. The bundled embedding model (all-MiniLM) is English,
# so we enrich each product's indexed text with English keywords. This is a
# cheap, honest stand-in for using a multilingual model server-side (see README).
JP_EN = {
    "ステンレス": "stainless steel", "鋼": "steel", "鋼板": "steel sheet",
    "アルミ": "aluminum", "アルミニウム": "aluminum", "銅": "copper",
    "樹脂": "resin plastic", "エポキシ": "epoxy", "シリコン": "silicon",
    "フィルム": "film", "ウェーハ": "wafer", "ガラス": "glass", "ゴム": "rubber",
    "線": "wire", "板": "plate sheet", "粉": "powder", "塗料": "paint coating",
    "接着": "adhesive", "繊維": "fiber", "セラミック": "ceramic",
}


# ----------------------------------------------------------------------------
# clouderby HTTP helpers
# ----------------------------------------------------------------------------
def _post(path, body, headers=None):
    data = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(BASE + path, data=data, method="POST")
    req.add_header("Content-Type", "application/json")
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=90) as r:
        return json.loads(r.read().decode("utf-8"))


def open_session():
    r = _post("/sessions", {"user": USER, "password": PASSWORD, "database": DATABASE})
    sid = r.get("session-id")
    if not sid:
        raise SystemExit("failed to open session: %s" % r)
    return sid


def sql(sid, statement, fetch_size=500):
    r = _post("/queries", {"sql": statement, "fetch-size": fetch_size},
              {"X-Clouderby-Session-Id": sid})
    if r.get("error"):
        raise SystemExit("SQL error: %s" % r.get("message", r))
    cols = [c["name"] for c in r.get("columns", [])]
    return [dict(zip(cols, row)) for row in r.get("rows", [])]


def vsearch(query, k=5):
    return _post("/vectors/search", {"q": query, "k": k}).get("hits", [])


def vupsert(doc_id, content):
    _post("/vectors/upsert", {"id": doc_id, "content": content})


# ----------------------------------------------------------------------------
# Commands
# ----------------------------------------------------------------------------
def enrich(name, ptype, desc):
    """Build the text we embed: Japanese name/desc + English keyword hints."""
    hints = [en for jp, en in JP_EN.items() if jp in (name or "") or jp in (desc or "")]
    parts = [ptype or "", name or "", desc or ""] + hints
    return " | ".join(p for p in parts if p)


def cmd_ingest():
    sid = open_session()
    rows = sql(sid, "SELECT PRODUCT_ID, PRODUCT_NAME, PRODUCT_TYPE, UNIT_PRICE, "
                    "DESCRIPTION FROM MDM_PRODUCTS WHERE IS_ACTIVE = '1'")
    print("indexing %d products into the clouderby vector index ..." % len(rows))
    for i, p in enumerate(rows, 1):
        content = enrich(p["PRODUCT_NAME"], p["PRODUCT_TYPE"], p.get("DESCRIPTION"))
        vupsert("prod:%s" % p["PRODUCT_ID"], content)
        if i % 50 == 0:
            print("  %d/%d" % (i, len(rows)))
    print("done. %d products indexed." % len(rows))


# Structured constraints a cheap regex can pull out — no LLM needed for these.
def extract_constraints(query):
    c = {}
    m = re.search(r"(?:under|<=?|以下|まで)\s*￥?\s*(\d+)", query) \
        or re.search(r"(\d+)\s*円\s*(?:以下|まで)", query)
    if m:
        c["max_price"] = float(m.group(1))
    return c


def stock_and_price(sid, product_id):
    rows = sql(sid,
               "SELECT p.PRODUCT_NAME AS name, p.PRODUCT_TYPE AS type, p.UNIT_PRICE AS price, "
               "COALESCE(SUM(i.QUANTITY), 0) AS qty, "
               "COALESCE(SUM(i.RESERVED_QTY), 0) AS reserved "
               "FROM MDM_PRODUCTS p LEFT JOIN INV_INVENTORY i ON i.PRODUCT_ID = p.PRODUCT_ID "
               "WHERE p.PRODUCT_ID = '%s' "
               "GROUP BY p.PRODUCT_NAME, p.PRODUCT_TYPE, p.UNIT_PRICE" % product_id)
    return rows[0] if rows else None


def cmd_search(query, k, max_price):
    sid = open_session()
    cons = extract_constraints(query)
    if max_price is not None:
        cons["max_price"] = max_price

    # 1) vector layer: free text -> nearest product ids (over-fetch for filtering).
    # Enrich the query with the same JP->EN hints used at index time, so the
    # English embedding model also handles Japanese phrasing symmetrically.
    qhints = [en for jp, en in JP_EN.items() if jp in query]
    enriched_q = " ".join([query] + qhints)
    hits = vsearch(enriched_q, max(k * 3, 10))
    if not hits:
        print("no matches (did you run `ingest` first?)")
        return

    print('\nquery: %r' % query)
    if cons:
        print("constraints (regex-extracted): %s" % cons)
    print("-" * 64)

    # 2) SQL layer: operate on the resolved rows (stock, price), apply constraints
    shown = 0
    for h in hits:
        pid = h["id"].split(":", 1)[1]
        rec = stock_and_price(sid, pid)
        if rec is None:
            continue
        price = rec.get("PRICE")
        if "max_price" in cons and price is not None and price > cons["max_price"]:
            continue
        avail = (rec.get("QTY") or 0) - (rec.get("RESERVED") or 0)
        flag = "  ⚠ LOW STOCK" if avail <= 0 else ""
        print("%-26s  %-12s  ¥%-7s  在庫 %6.0f (引当 %.0f)%s"
              % ((rec.get("NAME") or "")[:26], rec.get("TYPE") or "", _num(price),
                 avail, rec.get("RESERVED") or 0, flag))
        print("    match score=%.3f  id=%s" % (h["score"], pid))
        shown += 1
        if shown >= k:
            break
    if shown == 0:
        print("(matches found, but none passed the constraints)")


def _num(x):
    return "-" if x is None else ("%g" % x)


def cmd_demo():
    for q in ["ステンレスの薄い板", "epoxy resin", "アルミ 5052", "silicon wafer", "copper wire"]:
        cmd_search(q, k=3, max_price=None)


# ----------------------------------------------------------------------------
def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return
    cmd = args[0]
    if cmd == "ingest":
        cmd_ingest()
    elif cmd == "demo":
        cmd_demo()
    elif cmd == "search":
        rest = args[1:]
        k = 5
        max_price = None
        terms = []
        i = 0
        while i < len(rest):
            if rest[i] in ("-k", "--k") and i + 1 < len(rest):
                k = int(rest[i + 1]); i += 2
            elif rest[i] == "--max-price" and i + 1 < len(rest):
                max_price = float(rest[i + 1]); i += 2
            else:
                terms.append(rest[i]); i += 1
        if not terms:
            raise SystemExit('usage: search "<text>" [-k N] [--max-price N]')
        cmd_search(" ".join(terms), k, max_price)
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
