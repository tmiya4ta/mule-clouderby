"""
query_plan.py — the intent-analysis layer for material-search.

Turns a (possibly compound) natural-language request into a structured query
plan that conforms to PLAN_SCHEMA (a JSON Schema). The plan separates the three
kinds of content we discussed:

    "ステンレスの薄い板で500円以下、在庫があるやつを3件"
        ├─ semantic : "ステンレスの薄い板"   -> vector search
        ├─ filters  : price_max=500, in_stock=true  -> SQL WHERE
        └─ limit    : 3

The schema is the contract; how you *fill* it is a cost ladder:
  - parse_rule()  : deterministic regex/keyword rules. No dependency, no key.
  - parse_llm()   : optional. If ANTHROPIC_API_KEY is set, Claude fills the same
                    schema via tool use (robust for messy/compound phrasing).

parse() picks the LLM path when a key is present, else falls back to rules.
A tiny built-in validator checks the produced plan against PLAN_SCHEMA, so the
"structure" is actually enforced (stdlib only).
"""

import json
import os
import re
import urllib.request

# ---------------------------------------------------------------------------
# The contract: a JSON Schema for the structured query plan.
# ---------------------------------------------------------------------------
PLAN_SCHEMA = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "title": "MaterialQueryPlan",
    "type": "object",
    "additionalProperties": False,
    "required": ["intent", "semantic"],
    "properties": {
        "intent": {
            "type": "string",
            "enum": ["search", "stock_check", "unknown"],
            "description": "search: find products; stock_check: ask about availability",
        },
        "semantic": {
            "type": "string",
            "description": "the meaning-bearing part of the request, used for vector search",
        },
        "filters": {
            "type": "object",
            "additionalProperties": False,
            "properties": {
                "price_max": {"type": "number"},
                "price_min": {"type": "number"},
                "in_stock": {"type": "boolean"},
                "product_type": {
                    "type": "string",
                    "enum": ["RAW_MATERIAL", "SEMI_FINISHED", "FINISHED"],
                },
            },
        },
        "limit": {"type": "integer", "minimum": 1, "maximum": 50},
    },
}


# ---------------------------------------------------------------------------
# Minimal JSON Schema validator (covers the constructs PLAN_SCHEMA uses).
# ---------------------------------------------------------------------------
def validate(instance, schema=PLAN_SCHEMA, path="$"):
    errs = []

    t = schema.get("type")
    if t == "object":
        if not isinstance(instance, dict):
            return ["%s: expected object" % path]
        for req in schema.get("required", []):
            if req not in instance:
                errs.append("%s: missing required '%s'" % (path, req))
        props = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            for k in instance:
                if k not in props:
                    errs.append("%s: unexpected key '%s'" % (path, k))
        for k, v in instance.items():
            if k in props:
                errs += validate(v, props[k], "%s.%s" % (path, k))
    elif t == "string":
        if not isinstance(instance, str):
            errs.append("%s: expected string" % path)
        elif "enum" in schema and instance not in schema["enum"]:
            errs.append("%s: '%s' not in %s" % (path, instance, schema["enum"]))
    elif t == "number":
        if not isinstance(instance, (int, float)) or isinstance(instance, bool):
            errs.append("%s: expected number" % path)
    elif t == "integer":
        if not isinstance(instance, int) or isinstance(instance, bool):
            errs.append("%s: expected integer" % path)
        else:
            if "minimum" in schema and instance < schema["minimum"]:
                errs.append("%s: %d < minimum %d" % (path, instance, schema["minimum"]))
            if "maximum" in schema and instance > schema["maximum"]:
                errs.append("%s: %d > maximum %d" % (path, instance, schema["maximum"]))
    elif t == "boolean":
        if not isinstance(instance, bool):
            errs.append("%s: expected boolean" % path)
    return errs


# ---------------------------------------------------------------------------
# Rule-based parser (default; no dependency, no API key).
# ---------------------------------------------------------------------------
_TYPE_WORDS = {
    "原材料": "RAW_MATERIAL", "raw material": "RAW_MATERIAL",
    "半製品": "SEMI_FINISHED", "semi": "SEMI_FINISHED",
    "完成品": "FINISHED", "finished": "FINISHED",
}


def parse_rule(text):
    spans = []  # (start, end) of consumed substrings -> removed from semantic
    filters = {}

    def take(m):
        spans.append((m.start(), m.span()[1]))

    # price max
    for pat in (r"(\d+)\s*円?\s*(?:以下|まで)",
                r"(?:under|below|max|<=?)\s*[￥$]?\s*(\d+)"):
        m = re.search(pat, text, re.I)
        if m:
            filters["price_max"] = float(m.group(1)); take(m); break
    # price min
    for pat in (r"(\d+)\s*円?\s*以上",
                r"(?:over|above|min|>=?)\s*[￥$]?\s*(\d+)"):
        m = re.search(pat, text, re.I)
        if m:
            filters["price_min"] = float(m.group(1)); take(m); break
    # in stock
    m = re.search(r"在庫(?:が?ある|あり|有)|あるもの|在庫|in[\s-]?stock|available", text, re.I)
    if m:
        filters["in_stock"] = True; take(m)
    # product type
    for word, val in _TYPE_WORDS.items():
        m = re.search(re.escape(word), text, re.I)
        if m:
            filters["product_type"] = val; take(m); break
    # limit ("3件", "top 5")
    limit = None
    m = re.search(r"(\d+)\s*件", text) or re.search(r"top\s*(\d+)", text, re.I)
    if m:
        limit = int(m.group(1)); take(m)

    # semantic = original minus the consumed spans, cleaned up
    semantic = _strip_spans(text, spans)
    semantic = re.sub(r"[、,。\.]+", " ", semantic)
    semantic = re.sub(r"(を?教えて.*$|を?知りたい.*$|ください|下さい|ありますか)", " ", semantic)
    semantic = re.sub(r"(やつ|もの|物|こと)", " ", semantic)          # filler nouns
    semantic = re.sub(r"\s+", " ", semantic).strip(" 　・-")
    semantic = re.sub(r"[でをはがにのとや]+\s*$", "", semantic).strip()  # dangling particles
    if not semantic:
        semantic = text.strip()

    plan = {"intent": "search", "semantic": semantic}
    if filters:
        plan["filters"] = filters
    if limit:
        plan["limit"] = limit
    return plan


def _strip_spans(text, spans):
    if not spans:
        return text
    out, last = [], 0
    for s, e in sorted(spans):
        if s >= last:
            out.append(text[last:s]); last = e
    out.append(text[last:])
    return "".join(out)


# ---------------------------------------------------------------------------
# Optional LLM parser: Claude fills the SAME schema via tool use.
# Used only when ANTHROPIC_API_KEY is set; falls back to rules on any problem.
# ---------------------------------------------------------------------------
def parse_llm(text):
    key = os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        return None
    tool = {
        "name": "material_query_plan",
        "description": "Structured plan for a materials-catalog search request.",
        "input_schema": {k: v for k, v in PLAN_SCHEMA.items() if k != "$schema"},
    }
    body = {
        "model": os.environ.get("ANTHROPIC_MODEL", "claude-sonnet-4-6"),
        "max_tokens": 512,
        "tools": [tool],
        "tool_choice": {"type": "tool", "name": "material_query_plan"},
        "messages": [{
            "role": "user",
            "content": "Extract a query plan from this request. Put only the "
                       "meaning-bearing product description in `semantic`; put "
                       "prices/stock/type/limit in their fields.\n\n" + text,
        }],
    }
    req = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=json.dumps(body).encode("utf-8"), method="POST")
    req.add_header("content-type", "application/json")
    req.add_header("x-api-key", key)
    req.add_header("anthropic-version", "2023-06-01")
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            resp = json.loads(r.read().decode("utf-8"))
        for block in resp.get("content", []):
            if block.get("type") == "tool_use":
                return block["input"]
    except Exception as e:  # network/key/parse problems -> fall back
        print("  (LLM parse failed, using rules: %s)" % e)
    return None


# ---------------------------------------------------------------------------
def parse(text):
    """NL -> validated query plan. LLM if a key is set, else rules."""
    plan = parse_llm(text) or parse_rule(text)
    errs = validate(plan)
    if errs:
        # The LLM occasionally returns an off-schema value; fall back to rules.
        rule_plan = parse_rule(text)
        if not validate(rule_plan):
            return rule_plan, ["(llm plan invalid: %s)" % "; ".join(errs)]
        raise ValueError("plan failed schema validation: %s" % "; ".join(errs))
    return plan, []
