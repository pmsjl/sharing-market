"""生成与校园帖子语料对应、可重复执行的商品种子和回滚 SQL。

该批次只写入商品事实、规格和用途，不调用外部服务，也不读取任何凭据。
验货与交易建议由 Agent/RAG 在回答时提供，不能进入商品简介。
"""
from __future__ import annotations

import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SQL_DIR = ROOT / "market_backend/sql"
SEED_PATH = SQL_DIR / "20260819_post_aligned_commodity_seed.sql"
ROLLBACK_PATH = SQL_DIR / "20260819_post_aligned_commodity_seed_rollback.sql"
BATCH = "POST-ALIGNED-COMMODITY-20260819-V1"
SELLER_ID = 2074697289959530497

# 字段依次为：分类、名称、成色、价格、库存、图片标识、规格或状态、校园用途。
ROWS = [
    # 数码家电类：课程负载、扩展、账号锁、存储和接口。
    ("数码家电类", "ThinkPad T14 Gen 2 R7 32G 1T课程开发本", "八五新", 2680, 1, "Laptop-T14", "Ryzen 7 5850U、32GB内存、1TB NVMe SSD，卖家描述为双内存插槽版本", "Docker、WSL2、虚拟机和多服务联调"),
    ("数码家电类", "Dell Latitude 5420 i5 16G 512G商务本", "九成新", 1880, 1, "Laptop-5420", "i5-1145G7、16GB内存、512GB SSD，集成显卡", "基础编程、数据库、Office和日常携带"),
    ("数码家电类", "HP EliteBook 845 G8 R5 16G 512G轻薄本", "八五新", 1750, 1, "Laptop-845G8", "Ryzen 5 PRO 5650U、16GB内存、512GB SSD", "Java、Python、网页开发和教室移动使用"),
    ("数码家电类", "联想小新Pro14 R7 16G 512G高分屏本", "八成新", 1680, 1, "Laptop-Pro14", "Ryzen 7 5800H、16GB焊接内存、512GB SSD", "本地编译、数据处理和轻量创作"),
    ("数码家电类", "华硕天选3 i7 RTX3060 16G 1T游戏本", "八成新", 3690, 1, "Laptop-CUDA", "i7-12700H、RTX 3060 Laptop、16GB内存、1TB SSD", "明确需要CUDA、三维渲染或视频处理的课程项目"),
    ("数码家电类", "MacBook Air M1 16G 512G开发本", "八五新", 3980, 1, "Laptop-M1", "M1芯片、16GB统一内存、512GB存储", "跨平台开发、文档和续航优先的移动学习"),
    ("数码家电类", "三星980 PRO 1TB NVMe固态硬盘", "九成新", 420, 1, "NVMe-1TB", "M.2 2280 NVMe规格，卖家提供硬盘健康截图", "扩充Docker镜像、虚拟机和本地数据集空间"),
    ("数码家电类", "英睿达DDR4 3200 16G笔记本内存条", "九五新", 155, 2, "RAM-16G", "DDR4-3200 SODIMM单条16GB", "为可扩展二手本增加Docker和虚拟机余量"),
    ("数码家电类", "LG 27UP600 27英寸4K显示器", "八五新", 920, 1, "Monitor-4K", "27英寸、3840×2160、HDMI与DisplayPort接口", "代码多窗口、设计预览和宿舍桌面使用"),
    ("数码家电类", "Dell P2422H 24英寸升降显示器", "九成新", 590, 1, "Monitor-24", "24英寸1080P、升降旋转支架、HDMI与DP", "宿舍双屏编程和文档阅读"),
    ("数码家电类", "iPhone 13 128G已退出账号", "八成新", 2380, 1, "Phone-iPhone13", "128GB版本，卖家声明交付前退出个人账户", "日常通信、校园拍摄和移动应用测试"),
    ("数码家电类", "Redmi K50 12G 256G安卓手机", "八五新", 980, 1, "Phone-K50", "12GB内存、256GB存储，双卡版本", "安卓开发调试、备用机和校园日常使用"),
    ("数码家电类", "iPad 9 64G含原装笔记本保护套", "九成新", 1480, 1, "Tablet-iPad9", "64GB Wi-Fi版，含保护套，不含一次性软件权益", "电子教材、课堂笔记和文档批注"),
    ("数码家电类", "TP-LINK XDR3010 WiFi6路由器", "九成新", 145, 1, "Router-WiFi6", "AX3000级双频路由器，含匹配电源", "宿舍或合租房多设备联网"),
    ("数码家电类", "USB-C双HDMI九合一扩展坞", "八五新", 128, 2, "Dock-9in1", "含双HDMI、USB-A、读卡、网口和PD输入", "轻薄本外接显示器、网线和实验设备"),
    ("数码家电类", "绿联100W氮化镓三口充电器", "九成新", 168, 1, "Charger-100W", "双USB-C加USB-A，标称最高100W", "替代大体积笔记本与手机充电器"),
    ("数码家电类", "奥睿科USB4 NVMe硬盘盒", "九五新", 220, 2, "SSD-Enclosure", "M.2 NVMe硬盘盒，含数据线和散热垫", "课程项目、虚拟机和大文件移动存储"),
    ("数码家电类", "西部数据4TB移动硬盘含收纳包", "八五新", 460, 1, "HDD-4TB", "2.5英寸USB移动机械硬盘，标称4TB", "实验数据、课程资料和毕业设计多副本备份"),
    ("数码家电类", "罗技MX Master 3无线鼠标", "八成新", 285, 1, "Mouse-MX3", "蓝牙与接收器双连接，含接收器", "长时间编程、设计和多设备切换"),
    ("数码家电类", "便携USB逻辑分析仪16通道套装", "九成新", 260, 1, "Logic-Analyzer", "16通道逻辑分析仪，含测试夹线", "数字电路、嵌入式通信和协议调试"),

    # 办公用品类：课程实验器材与耗材成本。
    ("办公用品类", "STM32F407开发板含ST-Link与数据线", "九成新", 135, 2, "STM32-F407", "STM32F407核心开发板，含ST-Link下载器、USB线和排针", "嵌入式、自动化与电子类课程实验"),
    ("办公用品类", "ESP32 DevKitC无线开发板两块装", "九五新", 72, 3, "ESP32", "ESP32开发板两块，含Type-C数据线", "物联网、传感器联网和小型课程项目"),
    ("办公用品类", "Raspberry Pi Pico入门实验套件", "九成新", 88, 2, "Pico-Kit", "Pico主板、面包板、跳线、LED和常用电阻", "C语言、MicroPython与基础硬件实验"),
    ("办公用品类", "优利德UT61E数字万用表含表笔", "八五新", 230, 1, "Multimeter", "自动量程万用表，含红黑表笔和收纳包", "电路测量、电子工艺和维修排查"),
    ("办公用品类", "可调直流稳压电源30V5A", "八成新", 285, 1, "DC-Power", "单路0至30V、0至5A台式电源，含输出线", "电路实验、开发板供电和器件测试"),
    ("办公用品类", "T12数显焊台含三只烙铁头", "八五新", 168, 1, "Solder-T12", "T12数显焊台、手柄、支架和三只烙铁头", "电子工艺、焊接练习和开发板维修"),
    ("办公用品类", "USB隔离串口模块TTL调试套装", "九五新", 58, 4, "USB-TTL", "USB转TTL模块，支持常见电平切换，含杜邦线", "单片机串口、日志输出和固件调试"),
    ("办公用品类", "830孔面包板电子元件基础包", "九成新", 65, 3, "Breadboard", "面包板、跳线、电阻电容、按键和LED组合", "模电数电入门和临时电路搭建"),
    ("办公用品类", "TI-Nspire CX II图形计算器", "八五新", 620, 1, "Calculator-TI", "彩屏图形计算器，含充电线和保护壳", "明确允许使用图形计算器的课程和个人计算练习"),
    ("办公用品类", "兄弟HL-1218W黑白激光打印机", "八成新", 390, 1, "Printer-Laser", "无线黑白激光打印机，含电源线和剩余硒鼓", "宿舍打印报告、作业和社团材料"),
    ("办公用品类", "A4护眼阅读架可调角度金属款", "九五新", 48, 2, "Book-Stand", "金属折叠阅读架，多档角度", "教材阅读、抄写和桌面空间整理"),
    ("办公用品类", "工程制图工具套装含圆规三角板", "九成新", 42, 3, "Drawing-Kit", "圆规、三角板、比例尺、模板和自动铅笔", "工程制图、手绘草图和实验记录"),

    # 电器类：铭牌、功率、供电与清洁边界。
    ("电器类", "美的1.5L电热水壶带原配底座", "八成新", 55, 1, "Kettle", "1.5L壶体与原配底座，铭牌可读", "宿舍或租房日常烧水"),
    ("电器类", "德力西六位过载保护排插3米", "九成新", 48, 2, "Power-Strip", "六位插孔、3米线、带过载保护开关", "宿舍桌面电脑与低功率设备集中供电"),
    ("电器类", "海尔50L单门小冰箱", "八成新", 350, 1, "Mini-Fridge", "50L单门冷藏箱，含层架和电源线", "租房或允许使用的宿舍空间存放密封食品"),
    ("电器类", "美的3L电饭煲原装内胆", "八五新", 95, 1, "Rice-Cooker", "3L机械式电饭煲，原装内胆和电源线", "租房做饭或符合规定的生活空间"),
    ("电器类", "小米落地扇2直流变频版", "八五新", 145, 1, "Standing-Fan", "直流落地扇，含底座、电源线和遥控功能", "宿舍降温和夜间低噪使用"),
    ("电器类", "飞利浦HP8230吹风机", "八成新", 68, 1, "Hair-Dryer", "常规家用吹风机，线材完整", "日常吹干头发，需遵守所在宿舍用电规定"),
    ("电器类", "德尔玛除湿机12L家用款", "八成新", 420, 1, "Dehumidifier", "标称日除湿量12L，含水箱和电源线", "潮湿房间、租房或符合规定的宿舍环境"),
    ("电器类", "格兰仕20L机械式微波炉", "七成新", 210, 1, "Microwave", "20L机械旋钮微波炉，转盘与托环齐全", "租房公共厨房或明确允许使用的场所"),
    ("电器类", "荣事达1.2L电煮杯分体电源款", "九成新", 75, 1, "Cooking-Cup", "1.2L电煮杯，分体电源线与盖子齐全", "租房简餐或符合用电规定的生活空间"),
    ("电器类", "松下LED护眼台灯调光版", "九成新", 120, 1, "Desk-Lamp", "LED台灯，多档亮度与色温，含原配电源", "宿舍阅读、绘图和夜间学习"),

    # 日常用品类：尺寸、搬运、结构和真实成本。
    ("日常用品类", "20英寸静音万向轮登机箱", "八五新", 115, 1, "Luggage-20", "20英寸箱体，四组万向轮、拉杆和密码锁", "假期出行、毕业搬宿舍和短途旅行"),
    ("日常用品类", "捷安特Escape 2城市通勤自行车", "七成新", 780, 1, "Bike-City", "平把城市自行车，铝合金车架，多速传动", "校园通勤和周边短途骑行"),
    ("日常用品类", "折叠晾衣架双翼加厚款", "九成新", 58, 2, "Drying-Rack", "可折叠双翼结构，金属管架", "宿舍阳台、租房晾晒和毕业季转手"),
    ("日常用品类", "三层带轮宿舍收纳推车", "九五新", 65, 2, "Storage-Cart", "三层金属网篮、四只脚轮", "床边、桌旁和洗漱用品分类收纳"),
    ("日常用品类", "人体工学网布电脑椅可升降", "八成新", 260, 1, "Office-Chair", "网布靠背、升降气杆和扶手", "宿舍或租房长时间学习与编程"),
    ("日常用品类", "北弧F80显示器气压支架", "九成新", 125, 1, "Monitor-Arm", "桌夹式单屏支架，适配常见VESA孔位", "宿舍桌面释放空间和调整屏幕高度"),
    ("日常用品类", "宿舍床垫90×190厘米可折叠", "八成新", 80, 1, "Mattress", "90×190厘米折叠床垫，非贴身床品套件", "宿舍床位或临时住宿"),
    ("日常用品类", "家用工具箱含螺丝刀扳手套装", "九成新", 98, 1, "Toolbox", "螺丝刀、内六角、活动扳手、卷尺和常用小工具", "宿舍家具装配、自行车小调整和搬家拆装"),

    # 服装鞋帽类：尺码实测、磨损、安全与清洁。
    ("服装鞋帽类", "ASICS Gel-Kayano 28跑鞋 42码", "七成新", 165, 1, "Running-Shoes", "标注42码，鞋面有使用痕迹", "慢跑、体测训练前的低预算试穿选择"),
    ("服装鞋帽类", "迪卡侬MH500徒步鞋 41码", "八成新", 145, 1, "Hiking-Shoes", "标注41码，中帮徒步鞋", "社团徒步和周末短途活动"),
    ("服装鞋帽类", "未使用Giro Register骑行头盔 M码", "全新", 180, 1, "Helmet", "M码骑行头盔，卖家描述未佩戴，包装与调节器齐全", "校园骑行和通勤防护"),
    ("服装鞋帽类", "Nike 25L双肩电脑包", "八五新", 110, 1, "Backpack", "约25L容量，带独立电脑夹层", "携带笔记本、教材和日常通勤"),
    ("服装鞋帽类", "Columbia防水冲锋衣 L码", "八成新", 220, 1, "Jacket", "标注L码，带帽外层冲锋衣", "雨天通勤、社团户外和旅行备用"),
    ("服装鞋帽类", "尤尼克斯羽毛球鞋 43码", "七成新", 135, 1, "Badminton-Shoes", "标注43码，室内运动鞋", "体育课、社团训练和球馆使用"),

    # 宠物用品类：仅可清洁耐用品，不添加开封消耗品。
    ("宠物用品类", "中号可拆洗宠物航空箱", "八五新", 95, 1, "Pet-Carrier", "中号硬壳航空箱，门锁和提手齐全", "猫犬短途就医和搬运"),
    ("宠物用品类", "45厘米前开门玻璃爬宠箱", "八成新", 180, 1, "Terrarium", "45厘米玻璃箱体，前开门和通风网", "爬宠环境搭建或临时隔离"),
    ("宠物用品类", "三层实木猫爬架可拆装", "八成新", 150, 1, "Cat-Tree", "三层结构，含抓柱与平台，可拆装搬运", "宿舍外租房或家庭猫咪活动"),
    ("宠物用品类", "六片式金属宠物围栏", "九成新", 120, 1, "Pet-Fence", "六片可组合金属围栏，含连接件", "幼犬、小型宠物活动区和临时隔离"),
]


def key_for(category: str, name: str) -> str:
    suffix = hashlib.sha256(f"{category}|{name}".encode("utf-8")).hexdigest()[:16]
    return f"{BATCH}-{suffix}"


def sql_string(value: str) -> str:
    return "_utf8mb4'" + value.replace("\\", "\\\\").replace("'", "''") + "'"


def description_for(row: tuple) -> str:
    """只生成商品规格与用途，不把验货建议写入商品简介。"""
    _category, name, _degree, _price, _inventory, _image, spec, usage = row
    usage = usage.replace("慢跑、体测训练前的低预算试穿选择", "慢跑和体测训练")
    return f"{name}，{spec.strip('。')}。适合{usage.strip('。')}。"


def avatar_for(row: tuple) -> str:
    category, name, _degree, _price, _inventory, image, *_ = row
    return f"https://placehold.co/600x400/png?text={image}#{key_for(category, name)}"


def validate() -> None:
    assert len(ROWS) == 60, len(ROWS)
    names = [row[1] for row in ROWS]
    assert len(set(names)) == len(names)
    expected = {
        "数码家电类": 20,
        "办公用品类": 12,
        "电器类": 10,
        "日常用品类": 8,
        "服装鞋帽类": 6,
        "宠物用品类": 4,
    }
    actual = {category: sum(row[0] == category for row in ROWS) for category in expected}
    assert actual == expected, actual
    assert all(price > 0 and inventory > 0 for _, _, _, price, inventory, *_ in ROWS)


def build_seed() -> str:
    validate()
    values = []
    for category, name, degree, price, inventory, image, spec, usage in ROWS:
        key = key_for(category, name)
        description = description_for((category, name, degree, price, inventory, image, spec, usage))
        avatar = avatar_for((category, name, degree, price, inventory, image, spec, usage))
        values.append(
            "(" + ", ".join([
                sql_string(key), sql_string(category), sql_string(name),
                sql_string(description), sql_string(avatar), sql_string(degree),
                f"{price:.2f}", str(inventory),
            ]) + ")"
        )
    value_sql = ",\n".join(values)
    return f"""-- 与校园帖子语料对应的确定性商品批次。
-- 批次：{BATCH}；预期 60 条；卖家：syff（{SELLER_ID}）。
SET NAMES utf8mb4;
START TRANSACTION;

SET @seed_seller_id := (
    SELECT id FROM user
    WHERE id = {SELLER_ID} AND userName = 'syff' AND userAccount = 'syff' AND isDelete = 0
    LIMIT 1
);
SET @existing_batch_count := (
    SELECT COUNT(*) FROM commodity
    WHERE commodityAvatar LIKE '%#{BATCH}-%' OR commodityDescription LIKE '%资料条目标识：{BATCH}-%'
);
SET @required_category_count := (
    SELECT COUNT(*) FROM commodity_type
    WHERE typeName IN ('数码家电类','办公用品类','电器类','日常用品类','服装鞋帽类','宠物用品类')
      AND isDelete = 0
);

CREATE TEMPORARY TABLE post_aligned_commodity_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT post_aligned_commodity_guard_check CHECK (ok = 1)
);
INSERT INTO post_aligned_commodity_guard(ok)
VALUES (IF(
    @seed_seller_id IS NOT NULL
    AND @required_category_count = 6
    AND @existing_batch_count IN (0, 60),
    1, 0
));

CREATE TEMPORARY TABLE post_aligned_commodity_rows (
    seedKey VARCHAR(100) NOT NULL PRIMARY KEY,
    typeName VARCHAR(255) NOT NULL,
    commodityName VARCHAR(255) NOT NULL UNIQUE,
    commodityDescription VARCHAR(2048) NOT NULL,
    commodityAvatar VARCHAR(1024) NOT NULL,
    degree VARCHAR(255) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    inventory INT NOT NULL
);
INSERT INTO post_aligned_commodity_rows
(seedKey,typeName,commodityName,commodityDescription,commodityAvatar,degree,price,inventory)
VALUES
{value_sql};

UPDATE commodity existing
JOIN post_aligned_commodity_rows seed
  ON (existing.commodityAvatar LIKE CONCAT('%#',seed.seedKey)
      OR existing.commodityDescription LIKE CONCAT('%资料条目标识：',seed.seedKey,'%'))
JOIN commodity_type type ON type.typeName = seed.typeName AND type.isDelete = 0
SET existing.commodityName = seed.commodityName,
    existing.commodityDescription = seed.commodityDescription,
    existing.commodityAvatar = seed.commodityAvatar,
    existing.degree = seed.degree,
    existing.commodityTypeId = type.id,
    existing.adminId = @seed_seller_id,
    existing.isListed = 1,
    existing.commodityInventory = seed.inventory,
    existing.price = seed.price,
    existing.isDelete = 0,
    existing.updateTime = CURRENT_TIMESTAMP;

INSERT INTO commodity
(commodityName,commodityDescription,commodityAvatar,degree,commodityTypeId,
 adminId,isListed,commodityInventory,price,viewNum,favourNum,isDelete)
SELECT seed.commodityName,seed.commodityDescription,seed.commodityAvatar,seed.degree,
       type.id,@seed_seller_id,1,seed.inventory,seed.price,0,0,0
FROM post_aligned_commodity_rows seed
JOIN commodity_type type ON type.typeName = seed.typeName AND type.isDelete = 0
WHERE NOT EXISTS (
    SELECT 1 FROM commodity existing
    WHERE existing.commodityAvatar LIKE CONCAT('%#',seed.seedKey)
       OR existing.commodityDescription LIKE CONCAT('%资料条目标识：',seed.seedKey,'%')
)
AND NOT EXISTS (
    SELECT 1 FROM commodity existing
    WHERE existing.commodityName = seed.commodityName AND existing.isDelete = 0
);

INSERT INTO post_aligned_commodity_guard(ok)
SELECT IF(
    (SELECT COUNT(*) FROM commodity
     WHERE commodityAvatar LIKE '%#{BATCH}-%' OR commodityDescription LIKE '%资料条目标识：{BATCH}-%') = 60
    AND (SELECT COUNT(DISTINCT commodityName) FROM commodity
         WHERE commodityAvatar LIKE '%#{BATCH}-%' OR commodityDescription LIKE '%资料条目标识：{BATCH}-%') = 60
    AND NOT EXISTS (
        SELECT 1 FROM commodity
        WHERE commodityAvatar LIKE '%#{BATCH}-%' OR commodityDescription LIKE '%资料条目标识：{BATCH}-%'
          AND (adminId <> {SELLER_ID} OR isListed <> 1 OR commodityInventory <= 0
               OR price <= 0 OR isDelete <> 0)
    ),
    1, 0
);

DROP TEMPORARY TABLE post_aligned_commodity_rows;
DROP TEMPORARY TABLE post_aligned_commodity_guard;
COMMIT;
"""


def build_rollback() -> str:
    return f"""-- Safe rollback for batch {BATCH}.
SET NAMES utf8mb4;
START TRANSACTION;

SET @batch_reference_count := (
    SELECT
      (SELECT COUNT(*) FROM commodity_order o JOIN commodity c ON c.id=o.commodityId
       WHERE c.commodityAvatar LIKE '%#{BATCH}-%' OR c.commodityDescription LIKE '%资料条目标识：{BATCH}-%') +
      (SELECT COUNT(*) FROM commodity_score s JOIN commodity c ON c.id=s.commodityId
       WHERE c.commodityAvatar LIKE '%#{BATCH}-%' OR c.commodityDescription LIKE '%资料条目标识：{BATCH}-%') +
      (SELECT COUNT(*) FROM user_commodity_favorites f JOIN commodity c ON c.id=f.commodityId
       WHERE c.commodityAvatar LIKE '%#{BATCH}-%' OR c.commodityDescription LIKE '%资料条目标识：{BATCH}-%')
);
CREATE TEMPORARY TABLE post_aligned_rollback_guard (
    ok TINYINT NOT NULL,
    CONSTRAINT post_aligned_rollback_guard_check CHECK (ok = 1)
);
INSERT INTO post_aligned_rollback_guard(ok)
VALUES (IF(@batch_reference_count = 0, 1, 0));

DELETE FROM commodity
WHERE commodityAvatar LIKE '%#{BATCH}-%' OR commodityDescription LIKE '%资料条目标识：{BATCH}-%';

DROP TEMPORARY TABLE post_aligned_rollback_guard;
COMMIT;
"""


def main() -> None:
    SEED_PATH.write_text(build_seed(), encoding="utf-8", newline="\n")
    ROLLBACK_PATH.write_text(build_rollback(), encoding="utf-8", newline="\n")
    print(f"rows={len(ROWS)} seed={SEED_PATH.name} rollback={ROLLBACK_PATH.name}")


if __name__ == "__main__":
    main()
