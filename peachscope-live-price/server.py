import json
import os
import re
from datetime import datetime, timedelta, timezone
from functools import lru_cache
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import urlopen

AT_URL = "https://apis.data.go.kr/B552845/katRealTime2/trades2"
KST = timezone(timedelta(hours=9))
VARIETY_CODES = {"00", "11", "99"}


def fetch_rows(key, market, date):
    page = 1
    received = 0
    while True:
        query = urlencode({
            "serviceKey": key, "returnType": "json", "numOfRows": 500,
            "pageNo": page, "cond[whsl_mrkt_cd::EQ]": market,
            "cond[trd_clcln_ymd::EQ]": date,
            "cond[gds_lclsf_cd::EQ]": "06", "cond[gds_mclsf_cd::EQ]": "04",
        })
        with urlopen(f"{AT_URL}?{query}", timeout=8) as response:
            payload = json.load(response)
        if isinstance(payload.get("data"), list):
            rows = payload["data"]
            total = int(payload["totalCount"])
        else:
            response = payload["response"]
            code = str(response["header"]["resultCode"])
            if code not in {"0", "00", "000"}:
                raise ValueError("aT returned an error")
            body = response["body"]
            items = body.get("items") or {}
            rows = items.get("item", []) if isinstance(items, dict) else items
            if isinstance(rows, dict):
                rows = [rows]
            total = int(body["totalCount"])

        yield from rows
        received += len(rows)
        if received >= total:
            return
        if not rows or page >= 100:
            raise ValueError("Incomplete aT result")
        page += 1


def summarize(rows, auction_date, box_kg, grade):
    amount = 0.0
    boxes = 0.0
    count = 0
    last_auction_at = None
    for row in rows:
        if str(row.get("gds_sclsf_cd", "")).zfill(2) not in VARIETY_CODES:
            continue
        if str(row.get("unit_nm", "")).strip().lower() not in {"kg", "㎏"}:
            continue
        if grade and str(row.get("grd_cd", "")) != grade:
            continue
        try:
            sold_at = datetime.fromisoformat(str(row.get("scsbd_dt", "")))
            sold_at = sold_at.replace(tzinfo=KST) if sold_at.tzinfo is None else sold_at.astimezone(KST)
            weight = float(row["unit_qty"])
            price = float(row["scsbd_prc"])
            quantity = float(row["qty"])
        except (KeyError, TypeError, ValueError):
            continue
        if (sold_at.date().isoformat() != auction_date or weight != box_kg
                or not 0 < quantity < float("inf") or not 0 < price < float("inf")):
            continue
        amount += price * quantity
        boxes += quantity
        count += 1
        last_auction_at = max(last_auction_at or sold_at, sold_at)
    return {
        "status": "ok" if count else "no_trades",
        "auctionDate": auction_date,
        "boxKg": box_kg,
        "gradeCode": grade or None,
        "wonPerBox": round(amount / boxes) if count else None,
        "wonPerKg": round(amount / (boxes * box_kg)) if count else None,
        "tradeCount": count,
        "lastAuctionAt": last_auction_at.isoformat() if last_auction_at else None,
    }


@lru_cache(maxsize=64)
def get_price(market, date, box_kg, grade, cache_minute):
    key = os.environ["AT_SERVICE_KEY"].strip()
    if not key:
        raise ValueError("Missing aT API key")
    result = summarize(fetch_rows(key, market, date), date, box_kg, grade)
    return dict(result, marketCode=market, fetchedAt=datetime.now(KST).isoformat())


class PriceHandler(BaseHTTPRequestHandler):
    def send_json(self, status, data):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        url = urlsplit(self.path)
        if url.path != "/peach/live":
            self.send_json(404, {"status": "not_found"})
            return
        args = parse_qs(url.query)
        market = args.get("marketCode", ["110001"])[0]
        grade = args.get("gradeCode", [""])[0]
        raw_box = args.get("boxKg", ["4"])[0]
        if not re.fullmatch(r"\d{6}", market) or not re.fullmatch(r"\d{0,5}", grade) or raw_box not in {"2.5", "4", "5"}:
            self.send_json(400, {"status": "invalid_request"})
            return

        now = datetime.now(KST)
        try:
            data = get_price(market, now.date().isoformat(), float(raw_box), grade, int(now.timestamp() // 60))
        except (KeyError, OSError, ValueError, TypeError):
            self.send_json(502, {"status": "upstream_error"})
            return
        self.send_json(200, data)


if __name__ == "__main__":
    if not os.environ.get("AT_SERVICE_KEY", "").strip():
        raise SystemExit("Set AT_SERVICE_KEY before starting the server.")
    port = int(os.environ.get("PORT", "8000"))
    with ThreadingHTTPServer(("0.0.0.0", port), PriceHandler) as server:
        server.serve_forever()
