# INT-01/02 receiver contract — R04b/R04c

[BL-R08 Learning allocation extension](learning-allocation.md) đăng ký create/read attempt và
read/mutation classification theo action. Đây là contract cần implement/qualify trong R08;
qualification cũ không chứng minh các actions mới đã hỗ trợ.

[Canonical protocol](../../../docs/features/f10-identity-access/sessions/protocol.md). JSON strict UTF-8, duplicate/unknown fields rejected, schemaVersion integer1, UUID lowercase canonical, epoch bigint decimal string, UTC timestamps exactly millisecond `...SSSZ`. Body limit8192bytes. These are receiver contracts; R04d owns canonical INT-01 provider/coordinator. R04b qualification uses a real mTLS provider-contract fixture, not a canonical Identity substitute.

## INT-01 online read admission

POST `/internal/v1/auth-context/admissions` only from the Content service certificate to expected Identity service certificate. Fixed destination from owned-lab config; connect1s/request2s, no redirects or retry/ALLOW cache. Request contains schemaVersion, action=`CONTENT_BODY_READ`, audience=`content`, receiver-generated requestNonce, sessionCredential (only the app handle, never full Cookie), expectedContextId, method=`GET`, canonicalPath=`/api/v1/topics/{topicId}/units/{unitId}`, query empty, bodySha256=SHA256(empty), requestBinding. Content validates topic/unit IDs as 1–96 ASCII alphanumeric/underscore/hyphen characters with an alphanumeric first character. Query/body extensions belong to R07 and must update binding tests before exposure.

requestBinding = SHA256 UTF-8 of `method + LF + canonicalPath + LF + query + LF + bodySha256`. Raw password, owner/role or AuthzContext headers are not fields. No Content learner HTTP endpoint is opened by R04b; `ContentAccess.read` is the server-side endpoint integration port.

ALLOW response fields: schemaVersion, decision=`ALLOW`, admissionId, requestNonce, provider=`identity`, caller/audience=`content`, authRealmId, userId, sessionRef, sessionContextId, credentialEpoch, action, requestBinding, issuedAt, serverTime, validUntil, idleDeadline, absoluteDeadline, csrfVerified boolean. Read admission does not grant mutation rights. Non-200, malformed, wrong binding/audience/context/realm, unverified clock or missing budget fails closed. 500ms clock budget: serverTime must lie within own DB timestamps sampled immediately before and after the actual network request, expanded by500ms each side. A backward local interval is denied; the unused portion of the2s transport timeout does not expand this clock window. validUntil <= issuedAt+15s and both session deadlines; entry requires >6500ms left. DB transactions have5s statement/lock and6s total budget, final deadline check retains500ms margin and holds account gate until commit.

## Learning mutation admission — R04c

Learning direct mTLS INT01 request có cùng common binding fields nhưng action=`LEARNING_DRAFT_SAVE`, audience=`learning`, method=`PUT`, canonicalPath=`/api/v1/drafts/{draftId}` (canonical UUID), empty query, SHA256 validated body (max65536bytes), `origin` và `csrfToken` bắt buộc. Expected exact Origin do owned-lab config giữ. ALLOW caller/audience/action phải Learning và `csrfVerified=true`; sai Origin bị từ chối trước provider call. Nonce/context/realm/time/expiry và no-cache/no-redirect budgets giống Content.

`LearningAccess.mutate` là Java application port, chưa đăng ký API-L03 hoặc draft/CAS/T1/T2. Callback nhận own Connection và principal từ admission, phải kiểm resource owner và ghi effect/receipt trên Connection này. Admission-use + callback SQL commit/rollback atomic, cùng account gate giữ tới commit; fence phải chờ transaction đang chạy, late mutation sau durable fence không chạy callback. Không external side effect hoặc callback tự commit/close Connection.

Learning INT02 dùng cùng schema/digest bên dưới với audience/receiver=`learning`, own environment/generation/database. Không share runtime JAR hoặc truy cập Identity/Content DB. Static digest vẫn kết thúc `content,learning`; đổi audience không đổi static participant set. ACK chỉ là durable local evidence, chưa global completion.

## INT-02 durable fence (cả hai receiver)

PUT `/internal/v1/session-fences/{operationId}`, private8443, Identity certificate only. No Origin/browser Cookie/forwarded principal header. Public8080 and other certificates cannot reach the receiver.

Fields: schemaVersion, operationId, scope=`SESSION|ACCOUNT`, action, audience=`content`, userId, sessionRef/sessionContextId (both UUID for SESSION, both JSON null for ACCOUNT), epochFloor, authRealmId, topologyVersion=`r1-static-v1`, participantGeneration, commandDigest. SESSION actions LOGOUT/EXPIRY; ACCOUNT actions RESET/LOGOUT_ALL/PASSWORD_CHANGE/DISABLE/ENABLE/RECOVERY. ENABLE cannot lower a floor or remove a tombstone; lifecycle authority remains outside this receiver.

commandDigest follows R04a durable intents: SHA256 of `action LF userId LF (sessionRef or ACCOUNT) LF epochFloor LF authRealmId LF r1-static-v1 LF content,learning`. Receiver persists an additional envelope digest binding operation/scope/context/participant generation/audience; same operation with changed envelope returns409. A later operation covered by a higher floor returns durable provenance without lowering it.

ACK200 after own DB commit: schemaVersion, receiver=`content`, operationId, commandDigest, authRealmId, participantGeneration, topologyVersion, scope, appliedFloor string, sessionRef/sessionContextId nullable, durable=true. Receipt is immutable. Lost response causes resend; exact duplicate survives restart. ACK is participant evidence only; R04d must validate both static participants before COMPLETED.

Errors use the existing common JSON envelope with no cookie/credential/payload echo. No Set-Cookie. Runtime app cannot change environment realm/generation/readiness or erase fences/receipts. Operator provisioning is lab-only and forward/idempotent; recovery/retention/production handshake remain later owners.
