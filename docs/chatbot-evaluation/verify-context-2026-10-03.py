"""Record real chatbot replies for context and constraint regression checks."""
import argparse
import json
import secrets
import time
from pathlib import Path
import urllib.request

BASE = "http://localhost:4200/api/chat"
SUITES = {
    "filter_refinement": [
        "Giới thiệu 6 cuốn sách lập trình, giá mỗi cuốn tối đa 170k nhé.",
        "Chỉ lấy những cuốn dưới 100k thôi.",
        "Bỏ giới hạn giá, giữ nguyên chủ đề lập trình nhé.",
        "Còn cuốn nào khác cùng chủ đề không?",
    ],
    "explicit_anchor": [
        "Gợi ý vài cuốn sách trinh thám nhé.",
        "Gợi ý thêm những cuốn khác tương tự Clean Code nhé.",
        "Còn cuốn nào khác cùng chủ đề không?",
    ],
    "topic_or": [
        "Gợi ý vài cuốn trinh thám hoặc tình yêu, mỗi cuốn dưới 150k, tổng cộng tối đa 5 cuốn nhé.",
        "Còn cuốn nào khác cùng các chủ đề đó không?",
    ],
    "multi_topic": [
        "Gợi ý vài cuốn lập trình và vài cuốn trinh thám, tổng cộng không quá 5 cuốn nhé.",
    ],
    "excluded_language": [
        "Gợi ý sách lập trình, không lấy Java, chỉ lấy Python nhé.",
        "Còn cuốn nào khác không? Không lấy Java nhé.",
    ],
    "excluded_related": [
        "Gợi ý sách tương tự Clean Code, không lấy Java, mỗi cuốn dưới 150k nhé.",
        "Còn cuốn nào khác cùng chủ đề không?",
    ],
}


def request(url, method="GET", data=None):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, method=method,
                                 headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=60) as response:
        raw = response.read().decode("utf-8")
        return response.status, json.loads(raw) if raw else None


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", action="append", choices=list(SUITES))
    parser.add_argument("--output-prefix", default="results-context-live-2026-10-03")
    parser.add_argument("--append", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).parent
    target = root / (args.output_prefix + ".json")
    rows = json.loads(target.read_text(encoding="utf-8")) if args.append else []
    for suite, questions in SUITES.items():
        if args.suite and suite not in args.suite:
            continue
        session = "chat-" + secrets.token_hex(24)
        try:
            for index, question in enumerate(questions, 1):
                started = time.monotonic()
                status, response = request(BASE, "POST", {
                    "sessionId": session, "requestId": secrets.token_hex(16), "message": question,
                })
                case_id = f"{suite}-{index}"
                rows = [row for row in rows if row["id"] != case_id]
                rows.append({"id": case_id, "question": question, "status": status,
                             "elapsedMs": round(1000 * (time.monotonic() - started)), "response": response})
                target.write_text(json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
                print(json.dumps({"id": case_id, "tool": response.get("execution"),
                                  "fallback": response.get("fallback"), "answer": response.get("answer"),
                                  "books": [b.get("name") for b in response.get("products", [])]},
                                 ensure_ascii=False), flush=True)
        finally:
            request(BASE + "/conversation?sessionId=" + session, "DELETE")
    lines = ["# Phản hồi nguyên văn từ app — 03/10/2026", "",
             "Chạy qua proxy storefront localhost:4200 → backend Gemini 3.1 Flash Lite và MySQL. "
             "Mỗi bộ dùng phiên khách thử riêng; chỉ các phiên thử này được xóa sau khi lưu kết quả.", ""]
    for row in rows:
        lines.extend(["## " + row["id"], "", "**Câu hỏi:** " + row["question"], "",
                      "**Câu trả lời:**", "", row["response"]["answer"], ""])
    (root / (args.output_prefix + ".md")).write_text("\n".join(lines), encoding="utf-8")
