# BL-P02 — Nội dung bài mẫu English–Vietnamese

14/10/2026. User yêu cầu ưu tiên content và bài học song ngữ, thuật ngữ English chuẩn. Dependency P01 DONE. Goal: chuẩn biên soạn và bài HTTP mẫu song ngữ chạy được; REQ02/03/04/10/11/12/13, MD01/03/09.

AC: chuẩn canonical được nối từ business/REQ/F08/AGENTS; bài mẫu có EN/VI cho theory, objectives, captions, quiz/options/feedback và controls; chế độ EN/VI/both không mất trạng thái; code/đáp án chung; nguồn chuẩn; type/lint/build/browser và visual review. Ngoài scope: dịch toàn curriculum, backend localization/catalog/publication, server progress, sibling code.

Plan: brief → authoring standard → bilingual data/player → semantic review/test/screens → evidence. Brief chốt trước source edits.

## Checkpoint14/10/2026

DONE phạm vi prototype/content policy. Canonical standard ở F08 authoring/bilingual-standard.md; links AGENTS/business/REQ10/F08/template/curriculum/quality/F02 đồng nhất. `frontend/src/content/httpFoundations.ts` giữ14khối lý thuyết EN/VI theo ID,5questions chung đáp án. Player mặc định EN+VI có EN/VI radio, lang attributes, glossary, controls/caption/status/feedback/result song ngữ, một simulation/answers/score trong RAM. Không đổi IDs/rubric/backend schema/owner hoặc state R1. Code sample giữ identifiers và comment song ngữ; toàn shell/roadmap/curriculum chưa dịch.

Content review: giữ mục tiêu/phạm vi P01; đối chiếu14pairs/5question-option-feedback bằng đọc độc lập EN/VI, làm rõ stateless không cấm lưu application data, authentication khác authorization, idempotency không đảm bảo response giống nhau, database-error giả định chưa commit. Nguồn RFC9110 §9.2.2 và Java21 String isBlank/strip đã đọc14/10 qua nguồn chính thức; links cuối bài. Không coi test xanh thay expert/learner acceptance; user chưa đưa feedback về bản song ngữ này.

Verification: scripts/project.ps1 -Task check exit0 (TypeScript/ESLint + legacy schema); build final exit0. Edge browser `node node_modules/@playwright/test/cli.js test http-lesson.spec.ts` với PLAYWRIGHT_CHANNEL=msedge ngoài sandbox:3PASS/21.2s trên final build. Suite giữ test201/400/500/no result from navigation/retry/reload, kiểm toggle mid-simulation/mid-quiz/post-result giữ stage/choices/score, EN/VI feedback và responsive320/390/1440, keyboard/dark/reduced-motion; có screenshots bilingual1440/390 trong frontend/test-results. Đã xem hình desktop/mobile trước chỉnh CSS, phát hiện text Việt primary button chưa đủ tương phản; sửa color inherit và nút xếp text theo dòng, rebuild/retest3PASS và xem desktop cuối. Không rerun toàn app suite vì thay đổi giới hạn bài mẫu; P01 full11PASS là evidence lịch sử.

Doc checks PowerShell Test-Path/rg: standard/task có thật và entry links đủ; comment-rule heading anchor giữ nguyên. Một apply_patch ban đầu bị từ chối vì Delete/Add cùng target trong cùng patch; chưa có mutation từ patch đó, đã áp dụng lại theo các bước riêng và check/build đạt.

Bàn giao: mở /learn/http-foundations để trải nghiệm EN+VI/English/Tiếng Việt. Chưa dịch toàn khóa, chưa server-progress/persistence hoặc production publication; prototype không là hoàn thành leaf R1. Không commit/deploy/chat/agent.
