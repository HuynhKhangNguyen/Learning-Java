# Allocation metadata — private-case v3

Owner: Content sản xuất/publish, Learning verify/consume. Đây là contract được chốt cho BL-R08;
schema có tại [private-case-v3](private-case-v3.schema.json). Validator/runtime support phải được
implement và qualify trong R08; tài liệu/schema này chưa chứng minh runtime hỗ trợ v3.

V3 giữ id/unitId/expected/rubricTargets của private-case v2 và thêm object allocation exact fields:

- exerciseId: stable exercise/group được browser yêu cầu; server tìm candidate thuộc group này.
- mode: practice/assessment/project; server kiểm mode từ trusted metadata, không tin client override.
- caseVersion, rubricVersion, graderVersion: versions bất biến được pin cùng bundle/publication refs.
- familyId: family authored, không tạo mới bằng rename/shuffle input.
- exposureGroup: phạm vi nhóm assessment dùng để serialize allocation cùng owner/epoch.
- equivalenceKey: cùng giá trị nghĩa là nội dung tương đương về knowledge exposure; known bất kỳ
  member nào chặn clean assignment mới của cùng equivalenceKey xuyên version/epoch. Không suy ra
  mọi case cùng exercise/exposureGroup tương đương: family khác có thể là alternative còn clean.
- prompt: learner-visible bilingual en/vi. input: assigned learner-visible case data.
- inputSchema: schema cấu trúc câu trả lời được phép, không chứa expected/default/example tiết lộ đáp án.

Khi allocate, lưu owner, epoch, case ID/version, exercise/mode, family/exposureGroup/equivalenceKey,
rubric/grader versions và exact bundle/manifest digests. JRN-01 bind operation/attempt/owner và các
exposure refs; journal không chứa prompt/input/expected/submission body. Eligibility được snapshot
tại assignment; encounter của assignment đó chỉ làm known cho assignment tương lai.

## Validation và privacy

Content validator kiểm candidate component/reference/DAG và bind unit, case version, rubric targets
trước immutable publish. INT-03 không mang candidate graph, nên Learning không được claim tự chứng
minh graph đó; consumer kiểm lại exact v2/v3 bundle shape, allocation/schema profile, bundle/manifest
digests, policy tuples và coverage của mọi rubric target từ authenticated bundle + manifest thực nhận.
Không cần mở rộng wire chỉ để lặp lại graph validation thuộc Content. Duplicate case ID/version khác
bytes, conflicting mode/family mapping hoặc thiếu refs phải reject trước đổi good head. Practice
fixtures không dùng assessment families/equivalence để lộ case rồi giữ nhãn clean.

Toàn bộ private-case v3 bytes vẫn là private INT-03 bundle; checksum component case bind exact
bytes, gồm cả allocation. Không thêm sidecar không được digest/signature chain hiện hành cover.
Prompt/input/schema chỉ được Learning allowlist sang API-L02 sau receipt và recheck. Không copy
nguyên allocation object hoặc expected/rubricTargets/grader metadata ra public response. Public
catalog/body API-C01–03 vẫn không chứa assessment prompt hoặc private case.

inputSchema dùng JSON Schema draft-07 với bounded size/depth theo budget bundle hiện có, cấm $ref
và $id bên trong để không fetch network/file hoặc mở resolution khác. Content/replica kiểm object
schema hợp lệ theo validator hiện có; runtime không execute code, expression hoặc arbitrary schema
plugin. Đây chỉ là validation shape, không chấm hay trả semantic-correctness feedback.

## Compatibility/transition

1. Giữ private-case-v2.schema.json và existing v2 fixtures nguyên contract. Producer/consumer hỗ trợ
   exact discriminated schemaVersion2 hoặc3; unknown version/unknown fields fail closed.
2. V2 vẫn stage/fence/release và đọc historical bytes được như trước. V2 thiếu allocation không được
   allocate; trả unavailable/no eligible content rõ ràng, không migrate thành v3 bằng defaults/guess.
3. Thêm support v3 ở cả producer và consumer trước khi publish v3. Consumer cũ reject v3 an toàn,
   giữ good head/receipt; không giả N/N-1 interoperability nếu chưa có actual evidence.
4. Tạo candidate v3 mới với ID/version/digests/manifest/components hợp lệ qua normal publication
   coordinator. Không edit applied migration, old bundle, immutable receipt hoặc publication history.
   Nếu cần additive DB metadata/index thì migration mới thuộc service sở hữu, không cross-DB.
5. Required fixtures: v2 positive, v3 positive, unsupported-version, missing metadata, bad ref,
   equivalence conflict, private sentinel và legacy v2 allocation deny. Hai sides validate cùng bytes;
   packaged Content→Learning STAGE/FENCE/RELEASE→allocation chứng minh end-to-end transition.

BL-R08 không biên soạn/release toàn bộ ngân hàng Collections. Fixtures lab phải là authored/versioned
content có provenance qua pipeline thật; không hardcode case trong Learning runtime.


## R08 runtime schema profile

Java producer/consumer use a bounded structural draft-07 subset without extra dependencies:
object/array/string/integer/number/boolean/null types, properties/required/additionalProperties/items,
structural size/number bounds, enum, allOf/anyOf/oneOf/not and descriptive text. Unknown keywords,
$ref/$id, default/examples/const and depth over16/schema nodes over4096 fail closed. Authoring Ajv
still validates draft-07 syntax using the existing lockfile. No schema executes learner code or grades
answers. This explicit subset is a runtime compatibility limit; richer authored schema must be reviewed
and supported on both sides before publication.
