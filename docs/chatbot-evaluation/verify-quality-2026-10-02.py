"""Exercise the running storefront proxy; save complete responses using disposable guest sessions."""
import json
import argparse
import secrets
import time
import urllib.request
from pathlib import Path

BASE = "http://localhost:4200/api/chat"
SUITES = {
    "discovery_more": [
        "Gợi ý vài cuốn sách trinh thám đang bán nhé.",
        "Cuốn đầu tiên còn bao nhiêu bản?",
        "Phí giao hàng là bao nhiêu?",
        "Còn cuốn nào khác cùng chủ đề không?",
        "Còn cuốn nào khác cùng chủ đề nữa không?",
        "Còn cuốn nào khác không?",
    ],
    "discovery_six": [
        "Mình muốn 6 cuốn sách trinh thám. Nếu không đủ, có thể gợi ý sách gần chủ đề nhưng hãy nói rõ.",
        "Cho mình thêm 6 cuốn khác cùng chủ đề nhé.",
    ],
    "discovery_exact_only": [
        "Cho mình 6 cuốn trinh thám, chỉ đúng thể loại thôi, không lấy sách gần chủ đề.",
    ],
    "discovery_nine": ["Giới thiệu 9 cuốn sách lập trình đang có trong kho nhé."],
    "discovery_default": ["Gợi ý vài cuốn sách phát triển bản thân đang bán nhé."],
    "discovery_nearby": [
        "Giới thiệu 6 cuốn sách phiêu lưu nhé. Nếu thiếu có thể chọn vài cuốn gần chủ đề và nói rõ.",
        "Còn cuốn nào khác cùng chủ đề không?",
    ],
    "discovery_topic_change": [
        "Gợi ý vài cuốn trinh thám nhé.",
        "Gợi ý thêm vài cuốn về tình yêu, chuyển sang chủ đề này nhé.",
    ],
    "comparison": [
        "So sánh Nhà Giả Kim và Hoàng Tử Bé.",
        "Cuốn nào rẻ hơn?",
        "Cuốn rẻ hơn đó còn bao nhiêu bản?",
        "Nội dung cuốn còn lại là gì?",
        "Tôi muốn mua 2 bản cuốn đó.",
    ],
    "facts": [
        "Hoàng Tử Bé do ai viết, còn bán không?",
        "Cho tôi số trang và ISBN của Nhà Giả Kim; nếu chưa có thông tin được xác nhận thì nói rõ.",
        "Cửa hàng có sách tên “Nhật Ký Atlantis 2099” không? Nếu không có, đừng đoán giá.",
        "Sách “Hoàng Tử Bé” do ai viết và còn bán không?",
        "Cuốn đó hiện còn bao nhiêu bản?",
    ],
    "startup": [
        "Tôi muốn thử mở một công ty nhỏ và kiểm chứng ý tưởng trước khi đầu tư nhiều tiền. Nên đọc gì?",
    ],
    "coding": [
        "Mã nguồn của tôi ngày càng rối, tôi muốn đọc gì để viết dễ hiểu và dễ bảo trì hơn?",
    ],
    "self_help": [
        "Mình cần sách phát triển bản thân, khoảng 100.000đ một cuốn. Gợi ý vài lựa chọn hiện có nhé.",
        "Còn cuốn nào khác không?",
    ],
    "adventure": [
        "Gợi ý sách phiêu lưu đang bán.",
        "Còn cuốn nào khác không?",
        "Chuyển sang trinh thám nhé.",
        "Chỉ lấy những cuốn dưới 150.000đ.",
    ],
    "child": ["Mình mua quà cho bé 8 tuổi. Có sách nào nên cân nhắc?"],
    "policy_and_order": [
        "Đơn 290k và 310k thì phí ship thế nào?",
        "Chưa đăng nhập thì có xem được đơn hàng của tôi không?",
    ],
    "purchase_handoff": ["Tôi muốn mua 2 bản Hoàng Tử Bé."],
    "cancel_order": ["Hủy đơn hàng giúp tôi"],
}


def request(path, method="GET", data=None):
    body = None if data is None else json.dumps(data, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(path, data=body, method=method, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=45) as response:
        return response.status, response.read().decode("utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--suite", action="append", choices=list(SUITES))
    parser.add_argument("--output-prefix", default="results-quality-live-2026-10-02")
    parser.add_argument("--append", action="store_true")
    args = parser.parse_args()
    rows = []
    root = Path(__file__).parent
    if args.append:
        rows = json.loads((root / (args.output_prefix + ".json")).read_text(encoding="utf-8"))
    for suite, questions in SUITES.items():
        if args.suite and suite not in args.suite:
            continue
        session = "chat-" + secrets.token_hex(24)
        for index, question in enumerate(questions, 1):
            started = time.monotonic()
            status, raw = request(BASE, "POST", {
                "sessionId": session, "requestId": secrets.token_hex(16), "message": question,
            })
            response = json.loads(raw)
            rows = [row for row in rows if row["id"] != f"{suite}-{index}"]
            rows.append({"id": f"{suite}-{index}", "question": question, "status": status,
                         "elapsedMs": round(1000 * (time.monotonic() - started)), "response": response})
            (root / (args.output_prefix + ".json")).write_text(
                json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
            print(json.dumps({"id": rows[-1]["id"], "tool": response.get("execution"),
                              "fallback": response.get("fallback"), "answer": response.get("answer"),
                              "books": [b.get("name") for b in response.get("products", [])]}, ensure_ascii=False), flush=True)
        request(BASE + "/conversation?sessionId=" + session, "DELETE")
    lines = ["# Phản hồi nguyên văn từ app — 02/10/2026", "",
             "Chạy qua proxy storefront localhost:4200 → backend đang dùng Gemini 3.1 Flash Lite. "
             "Mỗi bộ dùng một phiên thử riêng; lịch sử phiên thử được xóa sau khi ghi kết quả.", ""]
    for row in rows:
        lines.extend(["## " + row["id"], "", "**Câu hỏi:** " + row["question"], "",
                      "**Câu trả lời:**", "", row["response"]["answer"], ""])
    (root / (args.output_prefix + ".md")).write_text("\n".join(lines), encoding="utf-8")
