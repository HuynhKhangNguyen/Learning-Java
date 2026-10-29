# Goal → Requirement → Module → Task → DoD → Evidence

> Trace v3 MS04: parent IDs R01–34 giữ để nhóm, leaf suffix/owner/dependency/AC ở [backlog](../delivery-backlog.md); [test-release plan](test-release-plan.md) nối từng SEC/DATA + business registries với fixtures/commands/evidence. REQ/goal không đổi vì tách service; chưa runtime evidence.

BL-MS01 nối REQ-01/09/14–16/18 và MD01–07/09–11 với [service/data/API catalog](../architecture/service-boundaries.md): AC1 ownership → OWN-01–14; AC2 API → API-I/C/L và INT/OPS; AC3 T1/T2/replica → mục 3/6; AC4 source/adapter → mục 7; AC5 review → MS01-W01–10/findings. Evidence thiết kế; auth/data protocols đã có MS02/03, runtime security/DB/fault/release còn MS04/BL-R, không dùng MS01 làm PASS triển khai.

BL-MS02 nối REQ-09/13–15/MD10 và MS01 INT-01/02/06 với [security design](../architecture/security-design.md) + [session LLD](../features/f10-identity-access/sessions/protocol.md): AC1 asset/trust/caller → TH-01–11, mục 2–4; AC2 auth lifecycle/races → AUTH-INV01–08/SEC-01–13; AC3 service identity/resource/CSRF/private content → CTL-01–11/SEC-14–27; AC4 secret/audit/supply-chain → CTL-12–16/SEC-28–30; AC5 MS-F02/05/08 và source → mục 8. Contract/test design; data/restore ở MS03, DB race/mTLS/browser/scan evidence còn MS04/code sau rebaseline.

BL-MS03 nối REQ-05/06/09/10/15/16 và MD01/04–07/10/11 với [data protocols](../architecture/data-protocols.md): AC1 local T1/T2/publication → DATA-INV01/04/05, PUB-01–07/DATA-01–09; AC2 delivery → EVT-01–05/INT-03–05/DATA-10–15; AC3 reset/delete → JRN-01–04/LIFE-01–06/DATA-16–23; AC4 restore → DATA-INV08–10/DATA-24–30; AC5 MS-F01/03/04/06 và source/catalog/checkpoint. [Publication LLD](../features/f08-content/publication/protocol.md) và [lifecycle/recovery LLD](../architecture/lifecycle-recovery-protocol.md) là evidence thiết kế, DB/storage/fault/drill evidence còn MS04/code. Không dùng diagram để claim durability hoặc official pass.

BL-MS04 nối REQ-11/13/14/15/16 và trace toàn R1 với [implementation plan](implementation-plan.md): AC1 stack/source/DB/listener→§2–4; AC2 task→73 IDs (35 giữ/38 child),14 parents/58 runtime leaves/DAG; AC3 test→SEC30/DATA30/FX10/CMD11 + IA/LD/AP/CA/CM/UX registry; AC4 release→REL-01–08/provider qualification/restore-before-pilot/credential recovery; AC5 MS-F07/09/docs/DoD/brief. Evidence là thiết kế/static review; scans/CI/builds/DB/fault/provider/drill/learner reports còn từng leaf, không PASS từ doc.

BL-D14 review REQ-09–16/18 trong [readiness review](../architecture/readiness-review.md): AC01 walk-through→RV-F01–05; AC02 findings→owner/expected; AC03 source fixes→R31a trước R14/R24a, catalog §4.1/WIRE-01–06, edge CTL-01/09, lifecycle §5.1/DATA-24/30, R15/R20 early UX; AC04 static links/IDs/DAG/test owners/scope→checkpoint backlog. Requirement/business không thêm scope hoặc nới invariant; bổ sung kế hoạch không là CODE hay runtime PASS.

BL-D11 · 30/09/2026, cập nhật MS04. Goal nhóm dưới để đọc [business](../business-design.md), không mục tiêu mới. Rxx là parent hoặc leaf nguyên ID; parent expand child ở backlog. Evidence **cần thu khi triển khai**. DoD ở [delivery contract](contract.md).

| Goal nhóm | REQ | Feature / module | Task chính | DoD đặc thù | Evidence cần thu |
|---|---|---|---|---|---|
| G01 Nhìn toàn cảnh và chọn đường | REQ-01 | F01 / MD01/02/09 | R05–07/14 | Hai roadmap, ID shared, planned rõ | Catalog + route tests |
| G01 | REQ-02 | F01/02 / MD01/02/09 | R05/15/22 | Prerequisite thật, ôn đủ dùng, không khóa tùy tiện | Refresh-card + suggestion cases |
| G02 Hiểu cơ chế qua thực hành | REQ-03 | F02 / MD03/09 | R16/17 | Input/action/biên đúng, có bản chữ | CM-01–25 + UI review |
| G02 | REQ-04 | F03 / MD03/04/09 | R10/18 | Feedback/support đúng, không tự pass | Practice + hint tests |
| G03 Chứng minh và áp dụng | REQ-05 | F04 / MD04/05/11 | R08–13/19/20 | Case mới, rubric theo CAP | CA/AP + grader/DB tests |
| G03 | REQ-06 | F04/05 / MD05 | R13/20–22 | Ba chiều, version/shared đúng | Projection/rebuild tests |
| G01 | REQ-07 | F05 / MD02/05/06/09 | R21/22 | Resume đúng, chỉ gợi ý nội dung khả dụng | History/deep-link/E2E |
| G03 | REQ-08 | F06 / MD05/07/09 | R22/23 | Readiness khác applied, CL-U-01 không P1 | U1–U5 + CA-12 |
| G04 Giữ dữ liệu an toàn theo user | REQ-09 | F07/10 / MD06/10/11 | R02/08/09/11/13/25–29/32 | Local atomic/CAS/retry/journal floors và three-DB restore đúng | DATA/LD/qualification/fault/drill report |
| G05 Nội dung và sản phẩm đáng tin | REQ-10 | F08 / MD01 | R05–07/30 | Version/review/publish hợp lệ | Validator + publish report |
| G05 | REQ-11 | Xuyên UI / MD09 | R14–24/33 | Keyboard/320px/motion/errors đạt | UX/E2E/visual review |
| G03 | REQ-12 | F02/04/06; F09 sau / MD03/04/05/07 | R12/17/20/23 | Đúng loại evidence, không Java/Docker claim giả | CA/AP và UI labels |
| G05 | REQ-13 | Xuyên feature/module | Mỗi task; R30–34 | DONE-01–05, learner test thực | Checkpoint + audit + learner report |
| G04 | REQ-14 | F10 / MD10/09 | R03/04/24/29b/31/32b | Login/logout/expiry/revoke/restore credential đúng | IA/SEC + DB/browser/recovery |
| G04 | REQ-15 | F10/11 / MD06/10/11 | R02/04/08–11/13/24–29/31/32 | A/B/caller/DB/storage cô lập, không owner client/old realm | SEC/DATA/owner negative + late response/recovery |
| G04 | REQ-16 | F11 / MD04/05/11 | R08–13/19–21 | Versioned draft/submission/history đúng | T1/T2 fault/race + API tests |
| G06 Quản lý học viên sau R1 | REQ-17 | F12 / MD10/11/12 | S05/E06 sau R1 | Chưa READY; phải chốt role/audit/acceptance | Spec + evidence sau, không đánh dấu đạt |
| G01 | REQ-18 | F01 / MD01/02/07 | R05–07/14/22; E05 nội dung sau | DevOps độc lập Java, Docker shared | DS/DP/reference + journey tests |

R00 giữ FE, R01a là leaf đầu khi CODE, R01b–d artifacts/bootstrap riêng. R28a trước cấp case/support/import; R28b qualified storage trước R32b drill/R31d pilot; R32b trước R31d không vòng. R31–34/REL gates không bỏ vì một slice chạy được. Bảng này không thay DAG ở backlog; một test có thể chứng minh nhiều REQ nếu oracle bao phủ invariant, không nhân bản chỉ để đủ hàng.

## Cách ghi bằng chứng khi làm

Trong checkpoint task, ghi `REQ → AC-ID → file/behavior → command/review → kết quả/giới hạn`. Một AC chưa kiểm hoặc fail không được coi là PASS từ test khác không liên quan. Khi thay scope, cập nhật bảng này, nguồn requirement và task tương ứng; giữ lịch sử quyết định thật, không sửa evidence cũ thành kết quả mới.
