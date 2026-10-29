# R06a candidate và INT-03 private bundle

## Candidate v1

`candidate-v1.schema.json` là executable structural contract. Java validator còn kiểm các invariant xuyên field mà JSON Schema không biểu diễn: `target=parent+1`, reference type, DAG, private-case binding, exact impact/manifest key set, policy matrix và review-subject digest. Candidate tối đa 524288 bytes; private bundle tối đa 1048576 bytes; manifest serialize từ `oldVersions` tối đa 262144 bytes.

Private bundle dùng đúng R05 `private-case-v2`: `{schemaVersion:2,id,unitId,expected,rubricTargets}`. `id` phải bind component `case` có SHA-256 bằng exact private bytes; `unitId` bind component `content`; mọi `rubricTargets` bind component `rubric`. Nó là grading input riêng, không được đưa vào public API/log/error.

Mỗi shared CAP có đúng một `impacts[]` classification và khai rõ tập `affectedVersions`. `oldVersions` có đúng một entry cho mỗi key `(capabilityId,version)` trong tập đó; nhiều old version cùng CAP được phép. Matrix v1:

| Classification | new | submit | finalize | current evidence | body |
|---|---:|---:|---:|---|---:|
| `COSMETIC` | true | true | true | `RETAIN` | true |
| `REVERIFY` | false | true | true | `REVERIFY` | true |
| `WITHDRAWN` | false | false | true | `WITHDRAW` | true |
| `INVALID_OLD_RUBRIC` | false | false | false | `REVIEW` | true |

Coverage/review không hash toàn candidate vì sẽ tạo self-digest vòng tròn. Cả hai dùng `subjectVersion:1` và cùng `subjectSha256`, được tính như sau:

1. Lấy đúng các field `schemaVersion`, `operationId`, `parentRevision`, `targetRevision`, `bundle`, `components`, `references`, `prerequisiteEdges`, `impacts`, `oldVersions`; loại hoàn toàn `coverage` và `review`.
2. Sort `components` theo `id`; references theo `(from,to,expectedType)`; edges theo `(from,to)`; impacts theo `capabilityId` và `affectedVersions` lexicographic; old versions theo `(capabilityId,version)`. Array khác giữ order.
3. Serialize compact UTF-8 JSON với object keys lexicographic, integer base-10, JSON string escaping chuẩn và không whitespace.
4. SHA-256 của bytes `backend-lab-publication-review-subject-v1\n` nối canonical JSON, encode lowercase hex.

`coverage.reportId` phải có prefix `coverage-`, status `PASSED`; `review.reportId` có prefix `review-`, status `APPROVED`. Mọi thay đổi metadata có nghĩa làm digest cũ bị từ chối; chỉ đổi thứ tự của semantic-set arrays không yêu cầu approval mới.

## INT-03 provider v1

Hai resource chỉ mở trên private mTLS listener cho certificate identity `learning` trong environment `lab`:

- `GET /internal/v1/content-bundles/{bundleId}` trả exact stored private bundle bytes (`application/octet-stream`).
- `GET /internal/v1/content-bundles/{bundleId}/manifest` trả exact stored manifest bytes (`application/json`).

Bundle response có `X-Content-Contract-Version: 1`, `X-Content-Revision`, `Digest: sha-256=<bundle hex>`, `X-Content-Manifest-Digest`, `X-Content-Manifest-Length` và bounded `Link` tới manifest resource. Nó **không** chở manifest payload trong header. Manifest response có cùng version/revision, own `Digest` và `X-Content-Bundle-Digest`. Cả hai có exact `Content-Length` và `Cache-Control: no-store`.

Provider rehash bundle lẫn manifest trước response. Unknown/unvalidated row trả 404; stored digest mismatch trả 503; lỗi không chứa payload. Public listener, cookie/Origin/caller header, query, encoded/ambiguous path và method khác bị từ chối trước payload.

Đây là provider layer R06a, không phải N/N-1 consumer qualification, STAGE/FENCE/COMMIT/RELEASE hoặc public content API.
