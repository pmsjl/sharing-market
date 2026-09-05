-- Safe rollback for batch PUBLIC-DEMO-COMMODITY-V1.
SET NAMES utf8mb4;
START TRANSACTION;

SET @batch_reference_count := (
    SELECT
      (SELECT COUNT(*) FROM commodity_order o JOIN commodity c ON c.id=o.commodityId
       WHERE c.commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR c.commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%') +
      (SELECT COUNT(*) FROM commodity_score s JOIN commodity c ON c.id=s.commodityId
       WHERE c.commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR c.commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%') +
      (SELECT COUNT(*) FROM user_commodity_favorites f JOIN commodity c ON c.id=f.commodityId
       WHERE c.commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR c.commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%')
);
CREATE TEMPORARY TABLE post_aligned_rollback_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT post_aligned_rollback_guard_check CHECK (ok = 1)
);
INSERT INTO post_aligned_rollback_guard(ok)
VALUES (IF(@batch_reference_count = 0, 1, 0));

DELETE FROM commodity
WHERE commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%';

DROP TEMPORARY TABLE post_aligned_rollback_guard;
COMMIT;
