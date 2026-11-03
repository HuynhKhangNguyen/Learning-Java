# Local journal lab

Chạy CMD-06 từ repository root:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline
```

Report nằm ở `artifacts/quality/R28a/<runId>/report.json`; nó bind manifest hash hiện hành của cả
tám Java main/test files cùng harness/contract, exact named cases, fault outcomes, fixed-head và
inventory digests, CAS retry, report/artifact hashes và packaged-class inspection. Root dữ liệu của
mỗi case do JUnit temporary directory sở hữu; process fixture phải exit và mọi root phải không còn
tồn tại trước khi report được ghi `PASS`.

Profile `qualification` cố ý trả `NOT_QUALIFIED`/nonzero cho đến BL-R28b. Local filesystem trên
một máy không chứng minh off-host durability, IAM, encryption, retention, provider consistency
hoặc disaster safety.
