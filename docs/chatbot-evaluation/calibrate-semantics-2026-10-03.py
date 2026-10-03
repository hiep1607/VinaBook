"""Read approved catalog vectors and record similarity for public test questions only."""
import json
import math
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
root = Path(__file__).resolve().parents[2] / "VinaBookStore"
env = os.environ.copy()
for line in (root / ".env.local").read_text(encoding="utf-8-sig").splitlines():
    if line.strip() and not line.lstrip().startswith("#") and "=" in line:
        key, value = line.split("=", 1)
        env[key.strip()] = value.strip().strip("\"'")
props = {}
for line in (root / "src/main/resources/application.properties").read_text(encoding="utf-8").splitlines():
    if "=" in line and not line.lstrip().startswith("#"):
        key, value = line.split("=", 1)
        props[key.strip()] = re.sub(r"\$\{([^}:]+)(?::([^}]*))?\}",
                                   lambda m: env.get(m[1], m[2] or ""), value.strip())
connection = re.search(r"jdbc:mysql://([^:/]+)(?::(\d+))?/([^?]+)", props["spring.datasource.url"])
env["MYSQL_PWD"] = props["spring.datasource.password"]
query = "SELECT JSON_OBJECT('id', product_id, 'summary', summary, 'vector', embedding_json) FROM book_profiles WHERE approved=1 AND embedding_json IS NOT NULL;"
result = subprocess.run([
    r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe", "--default-character-set=utf8mb4",
    "--batch", "--raw", "--skip-column-names", "--host", connection[1], "--port", connection[2] or "3306",
    "--user", props["spring.datasource.username"], connection[3], "-e", query,
], env=env, capture_output=True, encoding="utf-8")
if result.returncode:
    raise SystemExit("catalog_probe_failed")
books = [json.loads(line) for line in result.stdout.splitlines()]
questions = [
    "Mình hay trì hoãn công việc và muốn rèn những thay đổi nhỏ mỗi ngày",
    "Mình muốn biết cách kiểm chứng ý tưởng trước khi bỏ nhiều tiền mở một doanh nghiệp",
    "Mình muốn đọc câu chuyện có những bí mật cần suy luận để tìm lời giải",
    "Tìm sách về nuôi ong lấy mật",
]
key = next(env[name] for name in ["GEMINI_API_KEY_1", "GEMINI_API_KEY_2", "GEMINI_API_KEY_3", "AI_API_KEY"] if env.get(name))
rows = []
for question in questions:
    body = json.dumps({"content": {"parts": [{"text": question}]},
                       "embedContentConfig": {"taskType": "RETRIEVAL_QUERY", "outputDimensionality": 768}}).encode()
    req = urllib.request.Request("https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent",
                                 data=body, headers={"Content-Type": "application/json", "x-goog-api-key": key})
    try:
        vector = json.loads(urllib.request.urlopen(req, timeout=30).read())["embedding"]["values"]
    except Exception:
        raise SystemExit("embedding_probe_failed")
    ranked = []
    for book in books:
        other = json.loads(book["vector"])
        if len(vector) != len(other):
            continue
        dot = sum(a * b for a, b in zip(vector, other))
        norm = math.sqrt(sum(a * a for a in vector) * sum(b * b for b in other))
        ranked.append((dot / norm, book))
    top = sorted(ranked, key=lambda item: -item[0])[:6]
    rows.append({"question": question, "top": [{"id": book["id"], "score": round(score, 4),
                                               "summary": book["summary"]} for score, book in top]})
    print(json.dumps(rows[-1], ensure_ascii=False))
(Path(__file__).parent / "semantic-calibration-2026-10-03.json").write_text(
    json.dumps({"indexedProfiles": len(books), "queries": rows}, ensure_ascii=False, indent=2), encoding="utf-8")
