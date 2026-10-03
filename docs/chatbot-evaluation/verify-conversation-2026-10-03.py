"""Exercise the running storefront/API through its public tunnel using fresh guest sessions."""
import argparse
import json
import secrets
import sys
import time
import urllib.request
from pathlib import Path

SUITES = {
    "audience": [
        "Có cuốn sách nào các bạn nữ đặc biệt ưa thích không?",
        "Bạn ấy thích truyện trinh thám, chọn giúp mình 2 cuốn nhé.",
        "Tại sao lại là những cuốn đó?",
        "Bạn giải thích sâu hơn về những cuốn đó nhé.",
        "Còn cuốn nào khác cùng chủ đề không?",
    ],
    "list_reference": [
        "Gợi ý 2 cuốn sách lập trình dưới 170k nhé.",
        "Nói rõ hơn về cuốn đầu tiên.",
        "Tại sao lại là những cuốn đó?",
        "Tại sao lại là những cuốn đó?",
    ],
    "reading_goal": [
        "Mình hay trì hoãn công việc và muốn rèn những thay đổi nhỏ mỗi ngày, có cuốn nào phù hợp không?",
        "Vì sao chọn những cuốn đó?",
        "Nói kỹ hơn về những cuốn đó để mình quyết định nhé.",
    ],
    "return_missing_reason": [
        "Tôi muốn trả hàng.",
        "Sách bị lỗi in và thiếu trang.",
        "Tôi gửi lại sách về đâu?",
        "Bao lâu được hoàn tiền?",
    ],
    "return_changed_mind": [
        "Tôi vừa mua 1 cuốn sách trên cửa hàng bạn nhưng tôi chán rồi, muốn trả hàng",
        "Tìm trong giỏ hàng của tôi những cuốn sách đã mua",
    ],
    "return_reason_given": [
        "Tôi muốn đổi trả vì cửa hàng giao sai cuốn.",
        "Tôi muốn biết cách gửi lại sách.",
    ],
    "known_subject": ["Gợi ý sách pháp luật cho bạn gái đang học luật, tối đa 2 cuốn nhé."],
    "general_policy": ["Chính sách đổi trả là gì?"],
}

def call(base, path="", method="GET", payload=None):
    body=None if payload is None else json.dumps(payload,ensure_ascii=False).encode("utf-8")
    req=urllib.request.Request(base+path,data=body,method=method,headers={"Content-Type":"application/json","ngrok-skip-browser-warning":"true"})
    with urllib.request.urlopen(req,timeout=90) as res:
        raw=res.read().decode("utf-8")
        return res.status,json.loads(raw) if raw else None

def main():
    sys.stdout.reconfigure(encoding="utf-8")
    parser=argparse.ArgumentParser()
    parser.add_argument("--base",default="https://mortuary-unusable-collar.ngrok-free.dev/api/chat")
    parser.add_argument("--output",default="results-conversation-live-2026-10-03")
    parser.add_argument("--suite",action="append")
    parser.add_argument("--append",action="store_true")
    args=parser.parse_args()
    root=Path(__file__).parent
    target=root/(args.output+".json")
    rows=json.loads(target.read_text(encoding="utf-8")) if args.append and target.exists() else []
    for suite,questions in SUITES.items():
        if args.suite and suite not in args.suite:continue
        session="chat-"+secrets.token_hex(24)
        try:
            for number,question in enumerate(questions,1):
                started=time.monotonic()
                status,response=call(args.base,method="POST",payload={"sessionId":session,"requestId":secrets.token_hex(16),"message":question})
                row={"id":f"{suite}-{number}","question":question,"status":status,"elapsedMs":round(1000*(time.monotonic()-started)),"response":response}
                rows=[old for old in rows if old["id"]!=row["id"]]
                rows.append(row)
                (root/(args.output+".json")).write_text(json.dumps(rows,ensure_ascii=False,indent=2),encoding="utf-8")
                print(json.dumps({"id":row["id"],"status":status,"tool":response.get("execution"),"fallback":response.get("fallback"),"answerLength":len(response.get("answer","")),"books":[b["name"] for b in response.get("products",[])]},ensure_ascii=False),flush=True)
                # Respect the app's 20 requests/minute client limit, even for policy reads.
                time.sleep(max(0,3.2-(time.monotonic()-started)))
        finally:
            call(args.base,"/conversation?sessionId="+session,method="DELETE")
    lines=["# Câu trả lời nguyên văn — sửa ngữ cảnh, giải thích và đổi trả","",f"Kiểm thử API của app đang chạy qua `{args.base}`. Mỗi bộ dùng một phiên khách mới; chỉ các phiên kiểm thử này được xóa sau khi lưu kết quả. Không thao tác trên hội thoại của người dùng.",""]
    for row in rows:lines.extend(["## "+row["id"],"","**Câu hỏi:** "+row["question"],"","**Câu trả lời:**","",row["response"]["answer"],""])
    (root/(args.output+".md")).write_text("\n".join(lines),encoding="utf-8")
    by_id={row["id"]:row for row in rows}
    checks={"all_http_200":all(row["status"]==200 for row in rows),"no_fallback":all(not row["response"].get("fallback") for row in rows)}
    if "audience-1" in by_id:
        checks["gender_asks_preferences"] = not by_id["audience-1"]["response"].get("products") and "chủ đề" in by_id["audience-1"]["response"]["answer"]
        checks["why_keeps_list"] = [p["id"] for p in by_id["audience-2"]["response"]["products"]]==[p["id"] for p in by_id["audience-3"]["response"]["products"]]
        checks["deeper_answer_differs"] = by_id["audience-3"]["response"]["answer"]!=by_id["audience-4"]["response"]["answer"]
        checks["more_does_not_repeat_books"] = not (set(p["id"] for p in by_id["audience-2"]["response"]["products"]) & set(p["id"] for p in by_id["audience-5"]["response"]["products"]))
    if "list_reference-1" in by_id:
        checks["group_survives_singular_focus"]= [p["id"] for p in by_id["list_reference-1"]["response"]["products"]]==[p["id"] for p in by_id["list_reference-3"]["response"]["products"]]
        checks["identical_question_adds_new_reply"] = by_id["list_reference-3"]["response"]["answer"]!=by_id["list_reference-4"]["response"]["answer"]
    if "return_missing_reason-1" in by_id:
        checks["return_asks_reason_first"] = by_id["return_missing_reason-1"]["response"]["execution"]["status"]=="NEEDS_INPUT"
        checks["return_reason_guides"] = "thiếu trang" in by_id["return_missing_reason-2"]["response"]["answer"] and "2. Gửi yêu cầu" in by_id["return_missing_reason-2"]["response"]["answer"]
        checks["return_shipping_followup"] = "địa chỉ nhận lại" in by_id["return_missing_reason-3"]["response"]["answer"]
        checks["refund_followup"] = "thời gian hoặc cách hoàn tiền" in by_id["return_missing_reason-4"]["response"]["answer"]
    if "return_changed_mind-1" in by_id:
        checks["changed_mind_not_promised"] = "chưa có điều khoản" in by_id["return_changed_mind-1"]["response"]["answer"] and "Bạn muốn trả vì" not in by_id["return_changed_mind-1"]["response"]["answer"]
        checks["purchased_books_are_orders"] = "Sách đã mua nằm trong mục Đơn hàng" in by_id["return_changed_mind-2"]["response"]["answer"]
    if "known_subject-1" in by_id:checks["known_subject_still_recommends"]=bool(by_id["known_subject-1"]["response"].get("products"))
    if "reading_goal-2" in by_id:
        checks["empty_explanation_preserves_goal"]="yêu cầu bạn nêu" in by_id["reading_goal-2"]["response"]["answer"]
        checks["empty_followups_do_not_repeat"] = by_id["reading_goal-2"]["response"]["answer"]!=by_id["reading_goal-3"]["response"]["answer"]
    (root/(args.output+"-checks.json")).write_text(json.dumps(checks,indent=2),encoding="utf-8")
    print(json.dumps({"checks":checks,"turns":len(rows)},ensure_ascii=False),flush=True)

if __name__=="__main__":main()
