-- 公开演示账号，仅用于本地开发和课程展示，禁止用于生产环境。
-- 所有账号的明文演示密码均为 Demo123456，userPassword 为后端当前 SALT+MD5 结果。
SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO `user`
(`id`, `userAccount`, `userPassword`, `userName`, `userProfile`, `userRole`, `balance`, `isDelete`)
VALUES
  (2074697289959530497, 'demo_seller', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园集市演示卖家', '公开演示账号，不对应真实用户。', 'admin', 100000.00, 0),
  (2074697289959530501, 'seed_post_author_01', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者01', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530502, 'seed_post_author_02', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者02', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530503, 'seed_post_author_03', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者03', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530504, 'seed_post_author_04', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者04', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530505, 'seed_post_author_05', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者05', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530506, 'seed_post_author_06', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者06', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530507, 'seed_post_author_07', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者07', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530508, 'seed_post_author_08', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者08', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530509, 'seed_post_author_09', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者09', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530510, 'seed_post_author_10', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者10', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530511, 'seed_post_author_11', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者11', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530512, 'seed_post_author_12', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者12', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530513, 'seed_post_author_13', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者13', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530514, 'seed_post_author_14', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者14', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530515, 'seed_post_author_15', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者15', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530516, 'seed_post_author_16', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者16', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530517, 'seed_post_author_17', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者17', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530518, 'seed_post_author_18', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者18', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530519, 'seed_post_author_19', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者19', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0),
  (2074697289959530520, 'seed_post_author_20', '82dd756c96cffdaf7f89a4bf0d228ca8', '校园经验作者20', '公开演示账号，不对应真实用户。', 'user', 1000.00, 0)
ON DUPLICATE KEY UPDATE
  `userPassword` = VALUES(`userPassword`),
  `userName` = VALUES(`userName`),
  `userProfile` = VALUES(`userProfile`),
  `userRole` = VALUES(`userRole`),
  `balance` = VALUES(`balance`),
  `isDelete` = 0,
  `updateTime` = CURRENT_TIMESTAMP;

COMMIT;
