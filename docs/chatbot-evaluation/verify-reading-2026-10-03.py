"""Real app conversations: storefront proxy -> Gemini -> MySQL. Fresh guest sessions only."""
import argparse
import json
import secrets
import time
import urllib.request
from pathlib import Path

BASE = "http://localhost:4200/api/chat"
SUITES = {
    "soft_preference": [
        "Gợi ý 3 cuốn lập trình, ưu tiên Python, không lấy Java, mỗi cuốn dưới 170k nhé.",
        "Phí giao hàng là bao nhiêu?",
        "Còn cuốn nào khác cùng chủ đề không?",
        "Bỏ ưu tiên Python, giữ các điều kiện còn lại và tìm lại nhé.",
        "Bỏ loại trừ Java, giữ nguyên chủ đề và mức giá nhé.",
    ],
    "total_budget": [
        "Chọn giúp mình 3 cuốn lập trình, ưu tiên Python, không lấy Java, tổng ngân sách dưới 300k.",
        "Tăng tổng ngân sách lên 400k, chọn 4 cuốn nhé.",
        "Bỏ giới hạn tổng ngân sách, giữ chủ đề và ưu tiên cũ nhé.",
    ],
    "shortage": [
        "Giới thiệu 6 cuốn trinh thám, nếu thiếu thì có thể gợi ý gần chủ đề nhưng nói rõ nhé.",
        "Còn cuốn nào khác cùng chủ đề?",
        "Còn cuốn nào khác nữa không?",
    ],
    "mixed_intent": [
        "Gợi ý sách lập trình hoặc trinh thám, tổng cộng tối đa 5 cuốn, mỗi cuốn dưới 150k, và cho mình biết phí giao hàng.",
        "Còn cuốn nào khác cùng các chủ đề đó không?",
    ],
    "filter_refinement": [
        "Giới thiệu 6 cuốn lập trình, giá mỗi cuốn tối đa 170k nhé.",
        "Chỉ lấy các cuốn dưới 100k thôi.",
        "Bỏ giới hạn giá, giữ nguyên chủ đề lập trình nhé.",
        "Còn cuốn nào khác cùng chủ đề?",
    ],
    "strict_language": [
        "Gợi ý 6 cuốn lập trình, chỉ lấy Python, không lấy Java và không gợi ý gần chủ đề nhé.",
        "Còn cuốn nào khác không?",
    ],
    "count_is_not_money": [
        "Gợi ý trinh thám hoặc tình yêu, mỗi cuốn dưới 150k, tổng cộng tối đa 5 cuốn nhé.",
    ],
    "unknown_topic": ["Tìm sách về nuôi ong lấy mật nhé."],
    "semantic_goal": [
        "Mình hay trì hoãn công việc và muốn rèn những thay đổi nhỏ mỗi ngày, có sách nào phù hợp không?",
        "Mình muốn biết cách kiểm chứng ý tưởng trước khi bỏ nhiều tiền mở một doanh nghiệp.",
        "Mình viết code nhưng khó đọc lại và khó sửa khi yêu cầu thay đổi, nên đọc gì?",
        "Mình muốn đọc câu chuyện có những bí mật cần suy luận để tìm lời giải, có gợi ý nào không?",
    ],
}


def request(url, method="GET", data=None):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=90) as response:
        raw = response.read().decode("utf-8")
        return response.status, json.loads(raw) if raw else None


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", action="append", choices=list(SUITES))
    parser.add_argument("--output-prefix", default="results-reading-live-2026-10-03")
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
                print(json.dumps({"id": case_id, "status": status, "tool": response.get("execution"),
                                  "fallback": response.get("fallback"), "elapsedMs": rows[-1]["elapsedMs"],
                                  "books": [b.get("name") for b in response.get("products", [])]},
                                 ensure_ascii=False), flush=True)
        finally:
            request(BASE + "/conversation?sessionId=" + session, "DELETE")
    lines = ["# Phản hồi nguyên văn từ app — yêu cầu đọc và tìm theo ngữ nghĩa", "",
             "Chạy qua proxy storefront localhost:4200 → backend Gemini 3.1 Flash Lite và MySQL. "
             "Mỗi bộ dùng một phiên khách thử riêng; chỉ các phiên thử được xóa sau khi lưu kết quả. "
             "Đây là kiểm thử API của app đang chạy, không phải thao tác nhập từng câu qua giao diện.", ""]
    for row in rows:
        lines.extend(["## " + row["id"], "", "**Câu hỏi:** " + row["question"], "",
                      "**Câu trả lời:**", "", row["response"]["answer"], ""])
    (root / (args.output_prefix + ".md")).write_text("\n".join(lines), encoding="utf-8")
