# Local journal lab

Chạy CMD-06 từ repository root:

```powershell
pwsh -NoProfile -File scripts/services.ps1 -Task journal -Profile lab -Offline
```

Report nằm ở `artifacts/quality/R28a/<runId>/report.json` và chứa input/artifact/report hashes.
Root dữ liệu của mỗi case do JUnit temporary directory sở hữu và cleanup sau process fixtures.

Profile `qualification` cố ý trả `NOT_QUALIFIED`/nonzero cho đến BL-R28b. Local filesystem trên
một máy không chứng minh off-host durability, IAM, encryption, retention, provider consistency
hoặc disaster safety.
