#!/usr/bin/env python3
"""Generate the CSV seed data for the clouderby `finance` profile.

Deterministic (fixed seed) so the committed CSVs are reproducible.
Run from the repo root:

    python3 reference/mule/clouderby-mule-server/tools/gen_finance_data.py

Writes into src/main/resources/init/profiles/finance/csv/ and rewrites
identity_restart.sql so the Derby IDENTITY counters continue past the seed rows.
"""
import csv, os, random
from datetime import date, timedelta

random.seed(20260905)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "src", "main", "resources", "init", "profiles", "finance")
CSV_DIR = os.path.join(OUT, "csv")
os.makedirs(CSV_DIR, exist_ok=True)

def write(table, header, rows):
    path = os.path.join(CSV_DIR, table + ".csv")
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f, lineterminator="\n")
        w.writerow(header)
        w.writerows(rows)
    print(f"{len(rows):5d}  {table}")
    return rows

def d(dt):
    return dt.isoformat()

TODAY = date(2026, 9, 1)

# ---------------------------------------------------------------- BRN
BRANCH_DEFS = [
    ("BR001", "001", "本店営業部",       "HEAD_OFFICE",   "関東", "東京都千代田区丸の内1-1-1"),
    ("BR002", "013", "新宿支店",         "FULL_SERVICE",  "関東", "東京都新宿区西新宿2-8-1"),
    ("BR003", "021", "渋谷支店",         "FULL_SERVICE",  "関東", "東京都渋谷区道玄坂1-12-1"),
    ("BR004", "035", "横浜支店",         "FULL_SERVICE",  "関東", "神奈川県横浜市西区北幸1-4-1"),
    ("BR005", "104", "大阪支店",         "FULL_SERVICE",  "関西", "大阪府大阪市北区梅田3-1-3"),
    ("BR006", "112", "神戸支店",         "FULL_SERVICE",  "関西", "兵庫県神戸市中央区三宮町1-8-1"),
    ("BR007", "150", "名古屋支店",       "FULL_SERVICE",  "中部", "愛知県名古屋市中村区名駅1-1-4"),
    ("BR008", "201", "福岡支店",         "FULL_SERVICE",  "九州", "福岡県福岡市博多区博多駅前2-1-1"),
    ("BR009", "230", "札幌支店",         "FULL_SERVICE",  "北海道", "北海道札幌市中央区北5条西2-5"),
    ("BR010", "260", "仙台支店",         "FULL_SERVICE",  "東北", "宮城県仙台市青葉区中央1-3-1"),
    ("BR011", "301", "広島出張所",       "SUB_BRANCH",    "中国", "広島県広島市南区松原町2-37"),
    ("BR012", "900", "インターネット支店", "DIGITAL",      "全国", "-"),
]
branches = []
for i, (bid, code, name, btype, region, addr) in enumerate(BRANCH_DEFS):
    opened = date(1985 + i * 2, 4, 1)
    branches.append([bid, code, name, btype, region, addr,
                     f"0{random.choice([3,6,45,52,92,11,22])}-{random.randint(1000,9999)}-{random.randint(1000,9999)}",
                     f"EMP{i+1:04d}", d(opened), "ACTIVE"])   # 各支店の 1 人目が支店長
write("BRN_BRANCHES",
      ["BRANCH_ID","BRANCH_CODE","BRANCH_NAME","BRANCH_TYPE","REGION","ADDRESS","PHONE","MANAGER_ID","OPENED_DATE","STATUS"],
      branches)
BRANCH_IDS = [b[0] for b in branches]

SURNAMES = ["佐藤","鈴木","高橋","田中","伊藤","渡辺","山本","中村","小林","加藤",
            "吉田","山田","佐々木","山口","松本","井上","木村","林","斎藤","清水",
            "山崎","森","池田","橋本","阿部","石川","前田","藤田","後藤","小川"]
SURNAMES_KANA = ["サトウ","スズキ","タカハシ","タナカ","イトウ","ワタナベ","ヤマモト","ナカムラ","コバヤシ","カトウ",
                 "ヨシダ","ヤマダ","ササキ","ヤマグチ","マツモト","イノウエ","キムラ","ハヤシ","サイトウ","シミズ",
                 "ヤマザキ","モリ","イケダ","ハシモト","アベ","イシカワ","マエダ","フジタ","ゴトウ","オガワ"]
GIVEN_M = ["太郎","健一","翔","大輔","誠","拓也","隆","浩二","直樹","悠斗"]
GIVEN_M_KANA = ["タロウ","ケンイチ","ショウ","ダイスケ","マコト","タクヤ","タカシ","コウジ","ナオキ","ユウト"]
GIVEN_F = ["花子","美咲","恵子","彩","由美","さくら","裕子","愛","陽子","結衣"]
GIVEN_F_KANA = ["ハナコ","ミサキ","ケイコ","アヤ","ユミ","サクラ","ユウコ","アイ","ヨウコ","ユイ"]

def person(gender):
    i = random.randrange(len(SURNAMES))
    if gender == "M":
        j = random.randrange(len(GIVEN_M))
        return f"{SURNAMES[i]} {GIVEN_M[j]}", f"{SURNAMES_KANA[i]} {GIVEN_M_KANA[j]}"
    j = random.randrange(len(GIVEN_F))
    return f"{SURNAMES[i]} {GIVEN_F[j]}", f"{SURNAMES_KANA[i]} {GIVEN_F_KANA[j]}"

ROLES = ["支店長","副支店長","融資課長","窓口担当","法人営業","個人営業","事務担当","審査担当"]
employees = []
for i in range(40):
    eid = f"EMP{i+1:04d}"
    br = BRANCH_IDS[i % len(BRANCH_IDS)]
    g = random.choice(["M", "F"])
    name, kana = person(g)
    employees.append([eid, br, name, kana, random.choice(ROLES),
                      f"{eid.lower()}@example-bank.co.jp",
                      f"0{random.choice([3,6,45,52])}-{random.randint(1000,9999)}-{random.randint(1000,9999)}",
                      d(date(random.randint(1998, 2024), random.randint(1,12), random.choice([1,16]))),
                      "ACTIVE"])
write("BRN_EMPLOYEES",
      ["EMPLOYEE_ID","BRANCH_ID","EMPLOYEE_NAME","EMPLOYEE_KANA","ROLE","EMAIL","PHONE","HIRED_DATE","STATUS"],
      employees)
EMPLOYEE_IDS = [e[0] for e in employees]

atms = []
for i in range(20):
    br = BRANCH_IDS[i % len(BRANCH_IDS)]
    bname = next(b[2] for b in branches if b[0] == br)
    status = "ONLINE" if i not in (7, 15) else ("OUT_OF_SERVICE" if i == 7 else "LOW_CASH")
    atms.append([f"ATM{i+1:04d}", br, f"{bname} ATM{i%3+1}",
                 next(b[5] for b in branches if b[0] == br),
                 random.choice(["FULL_FUNCTION","CASH_ONLY","FULL_FUNCTION"]),
                 random.randrange(500_000, 9_000_000, 10_000), status,
                 d(TODAY - timedelta(days=random.randint(1, 60)))])
write("BRN_ATMS",
      ["ATM_ID","BRANCH_ID","LOCATION_NAME","ADDRESS","ATM_TYPE","CASH_BALANCE","STATUS","LAST_SERVICED_AT"],
      atms)

# ---------------------------------------------------------------- CIF
CORP_NAMES = ["株式会社ミナト製作所","日本橋商事株式会社","有限会社カワセ工業","株式会社さくらフーズ",
              "テクノアーク株式会社","中央物流株式会社","株式会社グリーンエナジー","北陸精機株式会社",
              "株式会社オーシャンリテール","明和建設株式会社","株式会社ヒカリ薬品","株式会社トウホク運輸"]
OCCUPATIONS = ["会社員","自営業","公務員","医師","弁護士","教員","年金受給者","学生","パート","経営者"]
SEGMENTS = ["MASS","MASS","MASS","AFFLUENT","AFFLUENT","PRIVATE"]

customers = []
for i in range(120):
    cid = f"CUST{i+1:05d}"
    if i < 12:
        ctype, name, kana = "CORPORATE", CORP_NAMES[i], ""
        dob, gender, occ = "", "", ""
        income = random.randrange(50_000_000, 3_000_000_000, 1_000_000)
        segment = "CORPORATE"
    else:
        ctype = "INDIVIDUAL"
        g = random.choice(["M", "F"])
        name, kana = person(g)
        gender = g
        dob = d(date(random.randint(1945, 2005), random.randint(1,12), random.randint(1,28)))
        occ = random.choice(OCCUPATIONS)
        income = random.randrange(2_000_000, 25_000_000, 100_000)
        segment = random.choice(SEGMENTS)
    risk = "HIGH" if i in (17, 44, 91) else ("MEDIUM" if i % 11 == 0 else "LOW")
    status = "DORMANT" if i in (33, 78) else ("CLOSED" if i == 105 else "ACTIVE")
    customers.append([cid, f"C{2000000+i*7:08d}", name, kana, ctype, dob, gender, "JP" if i != 63 else "US",
                      occ, income, f"user{i+1:03d}@example.com",
                      f"0{random.choice([80,90,70])}-{random.randint(1000,9999)}-{random.randint(1000,9999)}",
                      random.choice(BRANCH_IDS), segment, risk,
                      d(date(random.randint(2005, 2026), random.randint(1,12), random.randint(1,28))),
                      status])
write("CIF_CUSTOMERS",
      ["CUSTOMER_ID","CUSTOMER_NUMBER","CUSTOMER_NAME","CUSTOMER_KANA","CUSTOMER_TYPE","DATE_OF_BIRTH","GENDER",
       "NATIONALITY","OCCUPATION","ANNUAL_INCOME","EMAIL","PHONE","HOME_BRANCH_ID","SEGMENT","RISK_RATING",
       "ONBOARDED_DATE","STATUS"],
      customers)
CUSTOMER_IDS = [c[0] for c in customers]

PREFS = [("東京都","千代田区"),("東京都","新宿区"),("東京都","世田谷区"),("神奈川県","横浜市西区"),
         ("大阪府","大阪市北区"),("兵庫県","神戸市中央区"),("愛知県","名古屋市中区"),("福岡県","福岡市博多区"),
         ("北海道","札幌市中央区"),("宮城県","仙台市青葉区")]
addresses = []
aid = 1
for i, cid in enumerate(CUSTOMER_IDS):
    pref, city = random.choice(PREFS)
    addresses.append([aid, cid, "REGISTERED" if i < 12 else "HOME",
                      f"{random.randint(100,999)}-{random.randint(1000,9999)}", pref, city,
                      f"{random.randint(1,9)}-{random.randint(1,30)}-{random.randint(1,20)}",
                      d(date(random.randint(2005, 2025), random.randint(1,12), 1)), "1"])
    aid += 1
    if i % 12 == 5:  # 一部の顧客は勤務先/旧住所も持つ
        pref2, city2 = random.choice(PREFS)
        addresses.append([aid, cid, "WORK", f"{random.randint(100,999)}-{random.randint(1000,9999)}",
                          pref2, city2, f"{random.randint(1,9)}-{random.randint(1,30)}",
                          d(date(random.randint(2015, 2026), random.randint(1,12), 1)), "0"])
        aid += 1
write("CIF_ADDRESSES",
      ["ADDRESS_ID","CUSTOMER_ID","ADDRESS_TYPE","POSTAL_CODE","PREFECTURE","CITY","STREET","VALID_FROM","IS_PRIMARY"],
      addresses)
NEXT_ADDRESS_ID = aid

DOC_TYPES = ["運転免許証","マイナンバーカード","パスポート","健康保険証","登記事項証明書"]
kyc = []
kid = 1
for i, c in enumerate(customers):
    cid, onboard = c[0], c[15]
    doc = "登記事項証明書" if c[4] == "CORPORATE" else random.choice(DOC_TYPES[:4])
    kyc.append([kid, cid, "IDENTITY_VERIFICATION", onboard, doc,
                f"{random.randint(10**9, 10**10-1)}", "PASSED",
                d(date(int(onboard[:4]) + 10, int(onboard[5:7]), int(onboard[8:10]))),
                random.choice(EMPLOYEE_IDS), ""])
    kid += 1
    if c[14] in ("HIGH", "MEDIUM"):  # 高リスク顧客は継続的顧客管理 (EDD) が入る
        kyc.append([kid, cid, "ENHANCED_DUE_DILIGENCE",
                    d(TODAY - timedelta(days=random.randint(30, 700))), doc,
                    f"{random.randint(10**9, 10**10-1)}",
                    "PASSED" if c[14] == "MEDIUM" else "REVIEW_REQUIRED", "",
                    random.choice(EMPLOYEE_IDS), "リスク格付けに基づく強化デューデリジェンス"])
        kid += 1
write("CIF_KYC_CHECKS",
      ["CHECK_ID","CUSTOMER_ID","CHECK_TYPE","CHECK_DATE","DOCUMENT_TYPE","DOCUMENT_NUMBER","RESULT",
       "EXPIRES_AT","CHECKED_BY","NOTES"],
      kyc)
NEXT_KYC_ID = kid

REL_TYPES = ["SPOUSE","PARENT","CHILD","GUARANTOR","GROUP_COMPANY"]
rels = []
rid = 1
indiv = [c[0] for c in customers if c[4] == "INDIVIDUAL"]
for i in range(25):
    a, b = random.sample(indiv, 2)
    rels.append([rid, a, b, random.choice(REL_TYPES),
                 d(date(random.randint(2010, 2025), random.randint(1,12), 1)), ""])
    rid += 1
write("CIF_RELATIONSHIPS",
      ["RELATIONSHIP_ID","CUSTOMER_ID","RELATED_CUSTOMER_ID","RELATIONSHIP_TYPE","VALID_FROM","NOTES"],
      rels)
NEXT_REL_ID = rid

# ---------------------------------------------------------------- ACC
ACC_PRODUCT_DEFS = [
    ("AP001","SAV-STD",  "総合口座 (普通預金)",      "SAVINGS",      "JPY", 0,      0,   0.001, None),
    ("AP002","SAV-PLUS", "スーパー普通預金",          "SAVINGS",      "JPY", 100000, 0,   0.02,  None),
    ("AP003","CHK-STD",  "当座預金",                 "CHECKING",     "JPY", 0,      2200,0.0,   None),
    ("AP004","TD-6M",    "スーパー定期 6ヶ月",        "TIME_DEPOSIT", "JPY", 100000, 0,   0.15,  6),
    ("AP005","TD-1Y",    "スーパー定期 1年",          "TIME_DEPOSIT", "JPY", 100000, 0,   0.25,  12),
    ("AP006","TD-3Y",    "スーパー定期 3年",          "TIME_DEPOSIT", "JPY", 300000, 0,   0.35,  36),
    ("AP007","FX-USD",   "外貨普通預金 (米ドル)",     "FOREIGN",      "USD", 100,    0,   0.9,   None),
    ("AP008","FX-EUR",   "外貨普通預金 (ユーロ)",     "FOREIGN",      "EUR", 100,    0,   0.6,   None),
    ("AP009","SAV-JR",   "こども預金",               "SAVINGS",      "JPY", 0,      0,   0.05,  None),
    ("AP010","CHK-CORP", "法人当座預金",             "CHECKING",     "JPY", 500000, 5500,0.0,   None),
]
acc_products = []
for pid, code, name, ptype, cur, minb, fee, rate, term in ACC_PRODUCT_DEFS:
    acc_products.append([pid, code, name, ptype, cur, minb, fee, rate, term if term else "",
                         f"{name}。{'満期 ' + str(term) + 'ヶ月。' if term else ''}最低預入 {minb:,} {cur}。"])
write("ACC_PRODUCTS",
      ["PRODUCT_ID","PRODUCT_CODE","PRODUCT_NAME","PRODUCT_TYPE","CURRENCY","MIN_BALANCE","MONTHLY_FEE",
       "BASE_RATE","TERM_MONTHS","DESCRIPTION"],
      acc_products)

rates = []
rrid = 1
for p in acc_products:
    base = p[7]
    for yr, delta in ((2024, -0.05), (2025, 0.0), (2026, 0.05)):
        rates.append([rrid, p[0], "CREDIT", round(max(0.0, base + delta), 4),
                      d(date(yr, 4, 1)), d(date(yr + 1, 3, 31)) if yr < 2026 else ""])
        rrid += 1
write("ACC_INTEREST_RATES",
      ["RATE_ID","PRODUCT_ID","RATE_TYPE","RATE","EFFECTIVE_FROM","EFFECTIVE_TO"],
      rates)
NEXT_RATE_ID = rrid

accounts = []
active_customers = [c for c in customers if c[16] != "CLOSED"]
for i in range(200):
    cust = active_customers[i % len(active_customers)]
    cid = cust[0]
    corp = cust[4] == "CORPORATE"
    prod = random.choice(["AP010","AP003"] if corp else ["AP001","AP001","AP002","AP004","AP005","AP006","AP007","AP009"])
    pdef = next(p for p in acc_products if p[0] == prod)
    cur = pdef[4]
    if cur == "JPY":
        bal = random.randrange(50_000, 80_000_000 if corp else 12_000_000, 1000)
    else:
        bal = round(random.uniform(500, 60_000), 2)
    hold = round(bal * random.choice([0, 0, 0, 0.02, 0.05]), 2)
    closed = i in (40, 111, 176)
    accounts.append([f"ACC{i+1:06d}", f"{cust[12][-3:]}{2100000 + i * 37:07d}",
                     cid, prod, cust[12], cur,
                     0 if closed else bal, 0 if closed else round(bal - hold, 2), 0 if closed else hold,
                     500_000 if prod in ("AP003","AP010") else 0,
                     d(date(random.randint(2008, 2026), random.randint(1,12), random.randint(1,28))),
                     d(TODAY - timedelta(days=random.randint(10, 400))) if closed else "",
                     d(TODAY - timedelta(days=random.randint(0, 120))),
                     "CLOSED" if closed else ("FROZEN" if i in (17, 88) else "ACTIVE")])
write("ACC_ACCOUNTS",
      ["ACCOUNT_ID","ACCOUNT_NUMBER","CUSTOMER_ID","PRODUCT_ID","BRANCH_ID","CURRENCY","BALANCE",
       "AVAILABLE_BALANCE","HOLD_AMOUNT","OVERDRAFT_LIMIT","OPENED_DATE","CLOSED_DATE","LAST_ACTIVITY_AT","STATUS"],
      accounts)
OPEN_ACCOUNTS = [a for a in accounts if a[13] == "ACTIVE"]

# ---------------------------------------------------------------- CRD merchants
MERCHANT_DEFS = [
    ("セブン-イレブン","コンビニエンスストア","5411"),("ファミリーマート","コンビニエンスストア","5411"),
    ("イオンスタイル","スーパーマーケット","5411"),("マツモトキヨシ","ドラッグストア","5912"),
    ("ユニクロ","衣料品","5651"),("ビックカメラ","家電量販","5732"),
    ("Amazon.co.jp","ECモール","5942"),("楽天市場","ECモール","5399"),
    ("JR東日本","鉄道","4111"),("東京メトロ","鉄道","4111"),
    ("ENEOS","ガソリンスタンド","5541"),("スターバックス","カフェ","5814"),
    ("マクドナルド","ファストフード","5814"),("大戸屋","レストラン","5812"),
    ("ANA","航空","3000"),("JAL","航空","3001"),
    ("аpple.com","デジタルコンテンツ","5734"),("Google Play","デジタルコンテンツ","5734"),
    ("Netflix","動画配信","4899"),("Spotify","音楽配信","4899"),
    ("東京電力","公共料金","4900"),("東京ガス","公共料金","4900"),
    ("NTTドコモ","通信","4814"),("ソフトバンク","通信","4814"),
    ("三井住友海上","保険","6300"),("ヨドバシカメラ","家電量販","5732"),
    ("成城石井","スーパーマーケット","5411"),("すかいらーく","レストラン","5812"),
    ("Booking.com","旅行","4722"),("ホテルニューオータニ","宿泊","7011"),
]
merchants = []
for i, (name, cat, mcc) in enumerate(MERCHANT_DEFS):
    merchants.append([f"MER{i+1:04d}", name, cat, mcc,
                      "JP" if name not in ("Amazon.co.jp","Netflix","Spotify","Booking.com","Google Play") else random.choice(["US","NL","SE","US","IE"]),
                      random.choice(["東京","大阪","名古屋","福岡","横浜",""])])
write("CRD_MERCHANTS",
      ["MERCHANT_ID","MERCHANT_NAME","MERCHANT_CATEGORY","MCC","COUNTRY","CITY"],
      merchants)
MERCHANT_IDS = [m[0] for m in merchants]

cards = []
for i in range(90):
    acct = OPEN_ACCOUNTS[i % len(OPEN_ACCOUNTS)]
    ctype = "CREDIT" if i % 3 == 0 else "DEBIT"
    limit = random.choice([300_000, 500_000, 1_000_000, 2_000_000]) if ctype == "CREDIT" else 0
    used = round(limit * random.uniform(0, 0.7), 0) if limit else 0
    issued = date(random.randint(2020, 2026), random.randint(1,12), 1)
    cards.append([f"CARD{i+1:06d}",
                  f"{random.choice(['4','5'])}{random.randint(100,999)}-****-****-{random.randint(1000,9999)}",
                  acct[2], acct[0], ctype,
                  random.choice(["VISA","Mastercard","JCB"]),
                  limit, limit - used, d(issued),
                  d(date(issued.year + 5, issued.month, 1)),
                  "BLOCKED" if i in (23, 61) else ("EXPIRED" if i == 4 else "ACTIVE")])
write("CRD_CARDS",
      ["CARD_ID","CARD_NUMBER_MASKED","CUSTOMER_ID","ACCOUNT_ID","CARD_TYPE","BRAND","CREDIT_LIMIT",
       "AVAILABLE_CREDIT","ISSUED_DATE","EXPIRY_DATE","STATUS"],
      cards)
ACTIVE_CARDS = [c for c in cards if c[10] == "ACTIVE"]

# ---------------------------------------------------------------- TXN
TXN_TYPES = [("DEPOSIT","入金",1),("WITHDRAWAL","出金",-1),("TRANSFER_IN","振込入金",1),
             ("TRANSFER_OUT","振込出金",-1),("CARD_PAYMENT","カード決済",-1),
             ("FEE","手数料",-1),("INTEREST","利息",1)]
CHANNELS = ["ATM","BRANCH","ONLINE","MOBILE","MOBILE","ONLINE"]
txns = []
tid = 1
balances = {a[0]: a[6] for a in OPEN_ACCOUNTS}
for i in range(600):
    acct = OPEN_ACCOUNTS[i % len(OPEN_ACCOUNTS)]
    aid_ = acct[0]
    ttype, tdesc, sign = random.choice(TXN_TYPES)
    if ttype == "FEE":
        amt = random.choice([110, 220, 330, 550])
    elif ttype == "INTEREST":
        amt = round(balances[aid_] * 0.0001, 0)
    elif acct[5] != "JPY":
        amt = round(random.uniform(20, 3000), 2)
    else:
        amt = random.randrange(1000, 900_000, 1000)
    balances[aid_] = round(balances[aid_] + sign * amt, 2)
    tdate = TODAY - timedelta(days=random.randint(0, 180))
    merchant = random.choice(MERCHANT_IDS) if ttype == "CARD_PAYMENT" else ""
    cp = ""
    if ttype in ("TRANSFER_IN", "TRANSFER_OUT"):
        g = random.choice(["M","F"])
        cp = person(g)[0]
    txns.append([tid, f"TX{tdate.strftime('%Y%m%d')}{tid:06d}", aid_, d(tdate),
                 d(tdate + timedelta(days=0 if ttype != "TRANSFER_OUT" else 1)),
                 ttype, random.choice(CHANNELS), amt, acct[5], balances[aid_],
                 tdesc, cp, merchant, "POSTED", random.choice(EMPLOYEE_IDS) if random.random() < 0.2 else ""])
    tid += 1
write("TXN_TRANSACTIONS",
      ["TRANSACTION_ID","TRANSACTION_REF","ACCOUNT_ID","TRANSACTION_DATE","VALUE_DATE","TRANSACTION_TYPE",
       "CHANNEL","AMOUNT","CURRENCY","BALANCE_AFTER","DESCRIPTION","COUNTERPARTY_NAME","MERCHANT_ID",
       "STATUS","POSTED_BY"],
      txns)
NEXT_TXN_ID = tid

BANKS = [("0001","みずほ銀行"),("0005","三菱UFJ銀行"),("0009","三井住友銀行"),("0010","りそな銀行"),
         ("0033","PayPay銀行"),("0036","楽天銀行"),("9900","ゆうちょ銀行")]
transfers = []
trid = 1
for i in range(80):
    acct = random.choice(OPEN_ACCOUNTS)
    code, bank = random.choice(BANKS)
    tdate = TODAY - timedelta(days=random.randint(0, 180))
    amt = random.randrange(10_000, 3_000_000, 1000)
    failed = i in (12, 47, 66)
    transfers.append([trid, f"TR{tdate.strftime('%Y%m%d')}{trid:05d}", acct[0],
                      f"{random.randint(1000000, 9999999)}", code, bank, person(random.choice(["M","F"]))[1],
                      amt, "JPY", random.choice([110, 220, 440, 660]), d(tdate),
                      d(tdate + timedelta(days=random.choice([0, 0, 1, 3]))),
                      "FAILED" if failed else random.choice(["COMPLETED","COMPLETED","COMPLETED","PENDING"]),
                      random.choice(["受取口座名義相違","残高不足","受取口座解約済"]) if failed else ""])
    trid += 1
write("TXN_TRANSFERS",
      ["TRANSFER_ID","TRANSFER_REF","FROM_ACCOUNT_ID","TO_ACCOUNT_NUMBER","TO_BANK_CODE","TO_BANK_NAME",
       "BENEFICIARY_NAME","AMOUNT","CURRENCY","FEE","TRANSFER_DATE","SCHEDULED_DATE","STATUS","FAILURE_REASON"],
      transfers)
NEXT_TRANSFER_ID = trid

PAYEES = [("東京電力エナジーパートナー","MONTHLY"),("東京ガス","MONTHLY"),("NTTドコモ","MONTHLY"),
          ("ソフトバンク","MONTHLY"),("日本生命保険","MONTHLY"),("〇〇マンション管理組合","MONTHLY"),
          ("NHK","BIMONTHLY"),("さくら学園 授業料","MONTHLY"),("スポーツクラブ会費","MONTHLY"),
          ("住宅ローン返済","MONTHLY")]
orders = []
oid = 1
for i in range(40):
    acct = random.choice(OPEN_ACCOUNTS)
    payee, freq = random.choice(PAYEES)
    start = date(random.randint(2018, 2025), random.randint(1,12), random.choice([1, 27]))
    orders.append([oid, acct[0], payee, f"{random.randint(1000000, 9999999)}",
                   random.randrange(2000, 180_000, 500), "JPY", freq,
                   d(TODAY + timedelta(days=random.randint(1, 30))), d(start),
                   d(date(2029, 3, 31)) if i % 7 == 0 else "",
                   "SUSPENDED" if i in (9, 31) else "ACTIVE"])
    oid += 1
write("TXN_STANDING_ORDERS",
      ["ORDER_ID","ACCOUNT_ID","PAYEE_NAME","PAYEE_ACCOUNT","AMOUNT","CURRENCY","FREQUENCY",
       "NEXT_RUN_DATE","START_DATE","END_DATE","STATUS"],
      orders)
NEXT_ORDER_ID = oid

auths = []
auid = 1
for i in range(250):
    card = ACTIVE_CARDS[i % len(ACTIVE_CARDS)]
    mer = random.choice(MERCHANT_IDS)
    adate = TODAY - timedelta(days=random.randint(0, 120))
    declined = i % 23 == 0
    auths.append([auid, card[0], mer, d(adate), random.randrange(300, 250_000, 100), "JPY",
                  f"{random.randint(100000, 999999)}",
                  "DECLINED" if declined else "APPROVED",
                  random.choice(["与信限度額超過","カード無効","暗証番号相違"]) if declined else "",
                  "0" if declined else random.choice(["1","1","1","0"])])
    auid += 1
write("CRD_AUTHORIZATIONS",
      ["AUTH_ID","CARD_ID","MERCHANT_ID","AUTH_DATE","AMOUNT","CURRENCY","AUTH_CODE","RESULT",
       "DECLINE_REASON","IS_SETTLED"],
      auths)
NEXT_AUTH_ID = auid

# ---------------------------------------------------------------- ACC balance history
bal_hist = []
bhid = 1
for acct in OPEN_ACCOUNTS[:100]:
    closing = acct[6]
    for k in range(4):
        as_of = date(2026, 9 - k, 1) if 9 - k > 0 else date(2025, 12 + (9 - k), 1)
        debit = random.randrange(0, 400_000, 1000) if acct[5] == "JPY" else round(random.uniform(0, 2000), 2)
        credit = random.randrange(0, 500_000, 1000) if acct[5] == "JPY" else round(random.uniform(0, 2500), 2)
        opening = round(closing - credit + debit, 2)
        bal_hist.append([bhid, acct[0], d(as_of), opening, closing, debit, credit, acct[5]])
        bhid += 1
        closing = opening
write("ACC_BALANCE_HISTORY",
      ["HISTORY_ID","ACCOUNT_ID","AS_OF_DATE","OPENING_BALANCE","CLOSING_BALANCE","DEBIT_TOTAL",
       "CREDIT_TOTAL","CURRENCY"],
      bal_hist)
NEXT_BAL_ID = bhid

# ---------------------------------------------------------------- LON
LOAN_PRODUCT_DEFS = [
    ("LP001","MTG-FIX35","住宅ローン 全期間固定35年","MORTGAGE",   10_000_000, 100_000_000, 120, 420, 1.85),
    ("LP002","MTG-VAR",  "住宅ローン 変動金利",       "MORTGAGE",   10_000_000, 100_000_000, 120, 420, 0.475),
    ("LP003","AUTO-STD", "マイカーローン",            "AUTO",        300_000,   10_000_000,  12, 120, 2.45),
    ("LP004","EDU-STD",  "教育ローン",                "EDUCATION",   100_000,   10_000_000,  12, 180, 2.90),
    ("LP005","PSN-CARD", "カードローン",              "PERSONAL",    100_000,    5_000_000,  12,  60, 14.0),
    ("LP006","PSN-FREE", "フリーローン",              "PERSONAL",    100_000,    5_000_000,  12,  84, 6.80),
    ("LP007","BIZ-WC",   "事業性運転資金",            "BUSINESS",  1_000_000,  500_000_000,   6,  84, 1.60),
    ("LP008","BIZ-CAPEX","事業性設備資金",            "BUSINESS",  5_000_000, 1_000_000_000, 12, 240, 1.35),
]
lon_products = []
for pid, code, name, ltype, mn, mx, mnt, mxt, rate in LOAN_PRODUCT_DEFS:
    lon_products.append([pid, code, name, ltype, "JPY", mn, mx, mnt, mxt, rate,
                         f"{name}。借入 {mn:,}〜{mx:,}円、期間 {mnt}〜{mxt}ヶ月、基準金利 年{rate}%。"])
write("LON_PRODUCTS",
      ["LOAN_PRODUCT_ID","PRODUCT_CODE","PRODUCT_NAME","LOAN_TYPE","CURRENCY","MIN_AMOUNT","MAX_AMOUNT",
       "MIN_TERM_MONTHS","MAX_TERM_MONTHS","BASE_RATE","DESCRIPTION"],
      lon_products)

loans = []
for i in range(60):
    cust = active_customers[(i * 3) % len(active_customers)]
    corp = cust[4] == "CORPORATE"
    lp_id = (random.choice(["LP007", "LP008"]) if corp
             else random.choice(["LP001", "LP002", "LP003", "LP004", "LP005", "LP006"]))
    lp = next(p for p in lon_products if p[0] == lp_id)
    principal = random.randrange(int(lp[5]), int(min(lp[6], lp[5] * 12)), 100_000)
    term = random.choice([t for t in (12, 24, 36, 60, 84, 120, 240, 360, 420) if lp[7] <= t <= lp[8]])
    rate = round(lp[9] + random.uniform(-0.2, 0.6), 3)
    disbursed = date(random.randint(2015, 2026), random.randint(1,12), random.choice([1, 15]))
    elapsed = max(0, min(term, (TODAY.year - disbursed.year) * 12 + TODAY.month - disbursed.month))
    outstanding = round(principal * (1 - elapsed / term), 0) if term else principal
    delinq = random.choice([0]*17 + [15, 32, 61, 95])
    status = "PAID_OFF" if outstanding <= 0 else ("DELINQUENT" if delinq >= 30 else "ACTIVE")
    loans.append([f"LOAN{i+1:05d}", f"L{2026000 + i * 13:07d}", cust[0], lp[0], cust[12],
                  random.choice(EMPLOYEE_IDS), principal, max(0, outstanding), rate, term,
                  round(principal / term * (1 + rate / 100), 0) if term else 0,
                  d(disbursed), d(date(disbursed.year + term // 12, disbursed.month, disbursed.day)),
                  delinq, status])
write("LON_LOANS",
      ["LOAN_ID","LOAN_NUMBER","CUSTOMER_ID","LOAN_PRODUCT_ID","BRANCH_ID","OFFICER_ID","PRINCIPAL_AMOUNT",
       "OUTSTANDING_BALANCE","INTEREST_RATE","TERM_MONTHS","MONTHLY_PAYMENT","DISBURSED_DATE","MATURITY_DATE",
       "DELINQUENCY_DAYS","STATUS"],
      loans)

repay = []
rpid = 1
for ln in loans:
    loan_id, principal, rate, term, monthly = ln[0], ln[6], ln[8], ln[9], ln[10]
    disbursed = date.fromisoformat(ln[11])
    for n in range(1, min(term, 5) + 1):   # 直近 5 回分だけ持つ
        due_month = disbursed.month + n
        due = date(disbursed.year + (due_month - 1) // 12, (due_month - 1) % 12 + 1, 27)
        interest = round(principal * rate / 100 / 12, 0)
        principal_due = round(monthly - interest, 0)
        paid = due < TODAY and ln[14] != "DELINQUENT"
        repay.append([rpid, loan_id, n, d(due), principal_due, interest, monthly,
                      d(due) if paid else "", monthly if paid else "",
                      "PAID" if paid else ("OVERDUE" if due < TODAY else "SCHEDULED")])
        rpid += 1
write("LON_REPAYMENTS",
      ["REPAYMENT_ID","LOAN_ID","INSTALLMENT_NO","DUE_DATE","PRINCIPAL_DUE","INTEREST_DUE","TOTAL_DUE",
       "PAID_DATE","PAID_AMOUNT","STATUS"],
      repay)
NEXT_REPAY_ID = rpid

COLLATERAL_TYPES = [("REAL_ESTATE","土地・建物 (第一順位抵当権)"),("VEHICLE","車両 (所有権留保)"),
                    ("DEPOSIT","定期預金担保"),("SECURITIES","有価証券担保"),
                    ("GUARANTEE","信用保証協会保証")]
colls = []
cid_ = 1
for ln in loans:
    if ln[3] in ("LP001","LP002","LP003","LP007","LP008"):
        ctype, desc = random.choice(COLLATERAL_TYPES)
        appraised = round(ln[6] * random.uniform(1.1, 1.8), -4)
        colls.append([cid_, ln[0], ctype, desc, appraised,
                      d(date.fromisoformat(ln[11]) - timedelta(days=random.randint(10, 90))),
                      round(ln[6] / appraised * 100, 1), "PLEDGED"])
        cid_ += 1
write("LON_COLLATERALS",
      ["COLLATERAL_ID","LOAN_ID","COLLATERAL_TYPE","DESCRIPTION","APPRAISED_VALUE","APPRAISAL_DATE",
       "LTV_RATIO","STATUS"],
      colls)
NEXT_COLL_ID = cid_

# ---------------------------------------------------------------- RSK
def rating_of(score):
    for lim, r in ((800,"AAA"),(750,"AA"),(700,"A"),(650,"BBB"),(600,"BB"),(550,"B"),(0,"CCC")):
        if score >= lim:
            return r
scores = []
sid = 1
for c in customers:
    base = {"LOW": (660, 830), "MEDIUM": (580, 700), "HIGH": (430, 580)}[c[14]]
    sc = random.randint(*base)
    scores.append([sid, c[0], d(TODAY - timedelta(days=random.randint(1, 120))), sc,
                   "CIC-SCORE-v4" if c[4] == "INDIVIDUAL" else "CORP-RATING-v2",
                   rating_of(sc), round(max(0.02, (850 - sc) / 850 * 8), 2),
                   random.choice(["内部モデル","外部信用情報機関","内部モデル"])])
    sid += 1
write("RSK_CREDIT_SCORES",
      ["SCORE_ID","CUSTOMER_ID","SCORE_DATE","SCORE","SCORE_MODEL","RATING","PD_PERCENT","SOURCE"],
      scores)
NEXT_SCORE_ID = sid

exposures = []
eid = 1
loan_by_cust = {}
for ln in loans:
    loan_by_cust.setdefault(ln[2], 0)
    loan_by_cust[ln[2]] += ln[7]
for cust_id, amt in loan_by_cust.items():
    limit = round(amt * random.uniform(1.1, 2.0), -5) or 1_000_000
    exposures.append([eid, cust_id, d(date(2026, 8, 31)), "LOAN", amt, limit,
                      round(amt / limit * 100, 1), "JPY"])
    eid += 1
for c in random.sample(customers, 30):
    limit = random.randrange(300_000, 5_000_000, 100_000)
    used = round(limit * random.uniform(0, 0.9), -3)
    exposures.append([eid, c[0], d(date(2026, 8, 31)), "CARD", used, limit,
                      round(used / limit * 100, 1), "JPY"])
    eid += 1
write("RSK_EXPOSURES",
      ["EXPOSURE_ID","CUSTOMER_ID","AS_OF_DATE","EXPOSURE_TYPE","EXPOSURE_AMOUNT","LIMIT_AMOUNT",
       "UTILIZATION_PCT","CURRENCY"],
      exposures)
NEXT_EXPOSURE_ID = eid

# ---------------------------------------------------------------- AML
AML_RULES = [("R001","短期間の多額入出金 (Structuring)","HIGH"),
             ("R002","高リスク国向け送金","HIGH"),
             ("R003","口座開設直後の大口取引","MEDIUM"),
             ("R004","通常取引パターンからの逸脱","MEDIUM"),
             ("R005","現金取引のしきい値超過","MEDIUM"),
             ("R006","制裁リスト名寄せヒット (要確認)","CRITICAL"),
             ("R007","休眠口座の突然の再開","LOW")]
alerts = []
alid = 1
txn_by_id = {t[0]: t for t in txns}
for i in range(25):
    code, rname, sev = random.choice(AML_RULES)
    t = random.choice(txns)
    cust_of_acct = next(a[2] for a in accounts if a[0] == t[2])
    adate = t[3]
    closed = i % 3 != 0
    alerts.append([alid, f"AML-2026-{alid:04d}", cust_of_acct, t[0], adate, code, rname, sev,
                   "CLOSED_FALSE_POSITIVE" if closed else "OPEN",
                   random.choice(EMPLOYEE_IDS),
                   "調査の結果、通常の事業取引と確認。継続モニタリング対象。" if closed else "",
                   d(date.fromisoformat(adate) + timedelta(days=random.randint(3, 30))) if closed else ""])
    alid += 1
write("AML_ALERTS",
      ["ALERT_ID","ALERT_NUMBER","CUSTOMER_ID","TRANSACTION_ID","ALERT_DATE","RULE_CODE","RULE_NAME",
       "SEVERITY","STATUS","ASSIGNED_TO","RESOLUTION","CLOSED_DATE"],
      alerts)
NEXT_ALERT_ID = alid

WATCH_LISTS = ["OFAC SDN","EU Consolidated","国連安保理制裁リスト","国内 PEP リスト","内部要注意先"]
watch = []
wid = 1
for i in range(20):
    lst = random.choice(WATCH_LISTS)
    etype = random.choice(["INDIVIDUAL","ENTITY","VESSEL"])
    name = (f"SANCTIONED ENTITY {i+1:02d} LTD" if etype == "ENTITY"
            else (f"MV WATCHED VESSEL {i+1:02d}" if etype == "VESSEL" else f"DOE JOHN {i+1:02d}"))
    watch.append([wid, lst, name, etype,
                  random.choice(["KP","IR","RU","SY","CU","JP","XX"]),
                  d(date(random.randint(2014, 2026), random.randint(1,12), random.randint(1,28))),
                  f"REF-{random.randint(10000, 99999)}",
                  "テスト用のダミーデータ。実在の個人・団体とは無関係。"])
    wid += 1
write("AML_WATCHLIST",
      ["WATCHLIST_ID","LIST_NAME","ENTITY_NAME","ENTITY_TYPE","COUNTRY","LISTED_DATE","REFERENCE","NOTES"],
      watch)
NEXT_WATCH_ID = wid

# ---------------------------------------------------------------- identity restart
restarts = [
    ("CIF_ADDRESSES",      "ADDRESS_ID",      NEXT_ADDRESS_ID),
    ("CIF_KYC_CHECKS",     "CHECK_ID",        NEXT_KYC_ID),
    ("CIF_RELATIONSHIPS",  "RELATIONSHIP_ID", NEXT_REL_ID),
    ("ACC_BALANCE_HISTORY","HISTORY_ID",      NEXT_BAL_ID),
    ("ACC_INTEREST_RATES", "RATE_ID",         NEXT_RATE_ID),
    ("TXN_TRANSACTIONS",   "TRANSACTION_ID",  NEXT_TXN_ID),
    ("TXN_TRANSFERS",      "TRANSFER_ID",     NEXT_TRANSFER_ID),
    ("TXN_STANDING_ORDERS","ORDER_ID",        NEXT_ORDER_ID),
    ("CRD_AUTHORIZATIONS", "AUTH_ID",         NEXT_AUTH_ID),
    ("LON_REPAYMENTS",     "REPAYMENT_ID",    NEXT_REPAY_ID),
    ("LON_COLLATERALS",    "COLLATERAL_ID",   NEXT_COLL_ID),
    ("RSK_CREDIT_SCORES",  "SCORE_ID",        NEXT_SCORE_ID),
    ("RSK_EXPOSURES",      "EXPOSURE_ID",     NEXT_EXPOSURE_ID),
    ("AML_ALERTS",         "ALERT_ID",        NEXT_ALERT_ID),
    ("AML_WATCHLIST",      "WATCHLIST_ID",    NEXT_WATCH_ID),
]
with open(os.path.join(OUT, "identity_restart.sql"), "w", encoding="utf-8") as f:
    f.write("-- ============================================\n")
    f.write("-- IDENTITY Counter Restart (finance profile)\n")
    f.write("-- CSV で明示 ID を投入しているため、採番を続きから再開させる\n")
    f.write("-- 自動生成: tools/gen_finance_data.py\n")
    f.write("-- ============================================\n\n")
    for t, c, n in restarts:
        f.write(f"ALTER TABLE {t} ALTER COLUMN {c} RESTART WITH {n};\n")
    f.write("\nCOMMIT;\n")
print("identity_restart.sql written")
