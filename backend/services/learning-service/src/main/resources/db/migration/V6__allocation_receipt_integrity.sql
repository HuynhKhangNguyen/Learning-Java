-- BL-R08: PostgreSQL CHECK phải reject UNKNOWN; JSON null không là verified receipt.
ALTER TABLE learning.allocation_journal
  DROP CONSTRAINT allocation_journal_check;

ALTER TABLE learning.allocation_journal
  ADD CONSTRAINT allocation_journal_receipt_shape CHECK (COALESCE(
    (state = 'PENDING' AND receipt IS NULL)
    OR (
      state = 'VERIFIED'
      AND jsonb_typeof(receipt) = 'object'
      AND receipt ?& ARRAY['namespace', 'recordId', 'recordDigest', 'sequence',
        'nodeDigest', 'verifiedHeadDigest', 'casRetries']
      AND receipt - ARRAY['namespace', 'recordId', 'recordDigest', 'sequence',
        'nodeDigest', 'verifiedHeadDigest', 'casRetries'] = '{}'::jsonb
      AND receipt @> jsonb_build_object('namespace', namespace,
        'recordId', operation_id::text, 'recordDigest', record_sha256)
      AND jsonb_typeof(receipt->'sequence') = 'string'
      AND receipt->>'sequence' ~ '^[1-9][0-9]{0,18}$'
      AND (receipt->>'sequence')::numeric <= 9223372036854775807
      AND jsonb_typeof(receipt->'nodeDigest') = 'string'
      AND receipt->>'nodeDigest' ~ '^[0-9a-f]{64}$'
      AND jsonb_typeof(receipt->'verifiedHeadDigest') = 'string'
      AND receipt->>'verifiedHeadDigest' ~ '^[0-9a-f]{64}$'
      AND jsonb_typeof(receipt->'casRetries') = 'number'
      AND receipt->>'casRetries' ~ '^(0|[1-9][0-9]{0,9})$'
      AND (receipt->>'casRetries')::numeric <= 2147483647
    ), false));
