# Durable HTTP delivery foundation

MD06 per-service infrastructure, không shared domain JAR hoặc broker. [Registry](../../delivery-backlog.md) giữ trạng thái, [task R02c](tasks/BL-R02c.md) giữ goal/AC/DoD/checkpoint. [Data protocol](../../architecture/data-protocols.md) §3–4 là nguồn transport/envelope/order/recovery, [test-release](../../delivery/test-release-plan.md) giữ FX04/DATA10–15.

Producer local state+immutable outbox+sequence atomic; dispatcher claim lease rồi HTTP ngoài transaction. Receiver inbox+callback SQL+receipt+cursor atomic, ACK chỉ sau commit. Exact retry không effect lần hai; gap/poison/wrong ACK không advance. Auth fences monotonic MS02 có delivery lane riêng, không đứng sau publication/lifecycle poison.

Phạm vi R02c là nền port/harness/own migrations và kiểm PostgreSQL/mTLS thật. Publication/terminal deletion/global auth coordinator, journal/provider/backup/restore và production operations có owners riêng; việc tạo nền không nghiệm thu các nghiệp vụ này.

## Lệnh candidate foundation đã qualified

Từ repo root, runtime/cache đã pin theo [DB lab](../../../ops/db-lab/README.md) và [trust lab](../../../ops/trust-lab/README.md). Không dùng DB/service đang làm việc hoặc cài Windows service. Helper tạo fresh own cluster/roles/PKI, build ba artifact tuần tự cho relay, kiểm exact input hashes/reports và đóng own listeners trong finally. Fixed8080/8443 cần trống; không kill process lạ. Giữ nguyên source/POM/ops cả ba services, `scripts/services.ps1`/`db_lab.py` và HTTP delivery contract trong relay run.

```powershell
$env:BACKEND_LAB_JVM_MAX_HEAP_MB='384'
pwsh -NoProfile -File scripts/services.ps1 -Task db -Service identity -EvidenceTask R02c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task relay -Service learning -EvidenceTask R02c -Offline
pwsh -NoProfile -File scripts/services.ps1 -Task relay -Service content -Case DATA-14 -EvidenceTask R02c -Offline
pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -EvidenceTask R02c -RelayOnly
# Closing DoD cần full helper, không chỉ fast relay doubles:
pwsh -NoProfile -File scripts/tests/backend-build.Tests.ps1 -EvidenceTask R02c
```

CMD03 gồm fresh isolation/legacy suites rồi delivery-it không clean, giữ cả reports trên cùng owned DB. CMD05 `relay` có DATA10–15 selectors bằng explicit Failsafe methods, không gửi DATA case sang SEC/contract/fault profile. DATA15 chỉ Content/Learning actual INT02 receiver; canonical coordinator là R04d. Full relay yêu cầu tất cả named cases và variant matrix; selector yêu cầu đúng cases, nonzero/no skips/count match, không unrelated reports/cases PASS. `RelayOnly` chỉ fast helper doubles, không actual DB/mTLS và không thay full closing helper run. [Task checkpoint](tasks/BL-R02c.md) giữ exact actual runs/source version/limits; source có selector không có nghĩa selector đã qualified.

| Selector | Lớp FX04 chính; phải đi cùng final CMD03 foundation tests |
|---|---|
| DATA10 | Own producer effect/outbox và process crash trước/sau receiver commit; lease/exact resend, network không giữ DB locks |
| DATA11 | Exact duplicate/lost ACK/restart một effect, cùng ID changed bytes conflict |
| DATA12 | Future gap/poison no advance qua HTTP; retained/compacted-missing/corrupt covered proof và cursor trong CMD03 |
| DATA13 | Private/public/caller/header/body/schema limits, actual own-role maintenance ABORT/recovery, bounded network/TLS faults |
| DATA14 | Seven wrong ACK variants từ authenticated lab peer sau real receiver commit, producer không mark delivered |
| DATA15 | Independent Identity auth lane tới existing receiver INT02 khi strict poison vẫn giữ, không canonical completion |

Recovery fixture main/fault socket ở test classes, không packaged service JAR; terminal ABORT guard là local operation probe và trusted test-only own-producer status port, không production publication status provider. Registered recovery generation là coordinator version mới bound current realm/DB generations, không whole-system restore/traffic readiness. Retained receipts, paused messages, quarantine metadata và audit không tự xóa; không manual sửa bytes/reset cursor/unpause chỉ để làm candidate xanh. Failure evidence giữ riêng, source changed during run không PASS. Heap/short IPC/SerialGC/C1 là lab limits, không production SLA/load/Linux/container qualification.
