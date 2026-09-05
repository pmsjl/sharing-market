-- 与校园帖子语料对应的确定性商品批次。
-- 依赖：01_demo_users.sql、02_commodity_types.sql。
-- 批次：PUBLIC-DEMO-COMMODITY-V1；预期 60 条；卖家：demo_seller（2074697289959530497）。
SET NAMES utf8mb4;
START TRANSACTION;

SET @seed_seller_id := (
    SELECT id FROM user
    WHERE id = 2074697289959530497 AND userAccount = 'demo_seller' AND isDelete = 0
    LIMIT 1
);
SET @existing_batch_count := (
    SELECT COUNT(*) FROM commodity
    WHERE commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%'
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
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-5240877f73ec4127', _utf8mb4'数码家电类', _utf8mb4'ThinkPad T14 Gen 2 R7 32G 1T课程开发本', _utf8mb4'ThinkPad T14 Gen 2 R7 32G 1T课程开发本，Ryzen 7 5850U、32GB内存、1TB NVMe SSD，卖家描述为双内存插槽版本。适合Docker、WSL2、虚拟机和多服务联调。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-T14#PUBLIC-DEMO-COMMODITY-V1-5240877f73ec4127', _utf8mb4'八五新', 2680.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-1a1d85e58edac71a', _utf8mb4'数码家电类', _utf8mb4'Dell Latitude 5420 i5 16G 512G商务本', _utf8mb4'Dell Latitude 5420 i5 16G 512G商务本，i5-1145G7、16GB内存、512GB SSD，集成显卡。适合基础编程、数据库、Office和日常携带。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-5420#PUBLIC-DEMO-COMMODITY-V1-1a1d85e58edac71a', _utf8mb4'九成新', 1880.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-ee38cbc8bace3c32', _utf8mb4'数码家电类', _utf8mb4'HP EliteBook 845 G8 R5 16G 512G轻薄本', _utf8mb4'HP EliteBook 845 G8 R5 16G 512G轻薄本，Ryzen 5 PRO 5650U、16GB内存、512GB SSD。适合Java、Python、网页开发和教室移动使用。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-845G8#PUBLIC-DEMO-COMMODITY-V1-ee38cbc8bace3c32', _utf8mb4'八五新', 1750.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-6fe07b8a9c9a6669', _utf8mb4'数码家电类', _utf8mb4'联想小新Pro14 R7 16G 512G高分屏本', _utf8mb4'联想小新Pro14 R7 16G 512G高分屏本，Ryzen 7 5800H、16GB焊接内存、512GB SSD。适合本地编译、数据处理和轻量创作。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-Pro14#PUBLIC-DEMO-COMMODITY-V1-6fe07b8a9c9a6669', _utf8mb4'八成新', 1680.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-d99f39ccde8fe741', _utf8mb4'数码家电类', _utf8mb4'华硕天选3 i7 RTX3060 16G 1T游戏本', _utf8mb4'华硕天选3 i7 RTX3060 16G 1T游戏本，i7-12700H、RTX 3060 Laptop、16GB内存、1TB SSD。适合明确需要CUDA、三维渲染或视频处理的课程项目。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-CUDA#PUBLIC-DEMO-COMMODITY-V1-d99f39ccde8fe741', _utf8mb4'八成新', 3690.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-386c485f756c521e', _utf8mb4'数码家电类', _utf8mb4'MacBook Air M1 16G 512G开发本', _utf8mb4'MacBook Air M1 16G 512G开发本，M1芯片、16GB统一内存、512GB存储。适合跨平台开发、文档和续航优先的移动学习。', _utf8mb4'https://placehold.co/600x400/png?text=Laptop-M1#PUBLIC-DEMO-COMMODITY-V1-386c485f756c521e', _utf8mb4'八五新', 3980.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-58c6d49bcb407c9b', _utf8mb4'数码家电类', _utf8mb4'三星980 PRO 1TB NVMe固态硬盘', _utf8mb4'三星980 PRO 1TB NVMe固态硬盘，M.2 2280 NVMe规格，卖家提供硬盘健康截图。适合扩充Docker镜像、虚拟机和本地数据集空间。', _utf8mb4'https://placehold.co/600x400/png?text=NVMe-1TB#PUBLIC-DEMO-COMMODITY-V1-58c6d49bcb407c9b', _utf8mb4'九成新', 420.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-9bace48fbb6fc9d1', _utf8mb4'数码家电类', _utf8mb4'英睿达DDR4 3200 16G笔记本内存条', _utf8mb4'英睿达DDR4 3200 16G笔记本内存条，DDR4-3200 SODIMM单条16GB。适合为可扩展二手本增加Docker和虚拟机余量。', _utf8mb4'https://placehold.co/600x400/png?text=RAM-16G#PUBLIC-DEMO-COMMODITY-V1-9bace48fbb6fc9d1', _utf8mb4'九五新', 155.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-830fcd61a41cf7a9', _utf8mb4'数码家电类', _utf8mb4'LG 27UP600 27英寸4K显示器', _utf8mb4'LG 27UP600 27英寸4K显示器，27英寸、3840×2160、HDMI与DisplayPort接口。适合代码多窗口、设计预览和宿舍桌面使用。', _utf8mb4'https://placehold.co/600x400/png?text=Monitor-4K#PUBLIC-DEMO-COMMODITY-V1-830fcd61a41cf7a9', _utf8mb4'八五新', 920.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-b8c782183347be66', _utf8mb4'数码家电类', _utf8mb4'Dell P2422H 24英寸升降显示器', _utf8mb4'Dell P2422H 24英寸升降显示器，24英寸1080P、升降旋转支架、HDMI与DP。适合宿舍双屏编程和文档阅读。', _utf8mb4'https://placehold.co/600x400/png?text=Monitor-24#PUBLIC-DEMO-COMMODITY-V1-b8c782183347be66', _utf8mb4'九成新', 590.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-a4ac21fbeab03739', _utf8mb4'数码家电类', _utf8mb4'iPhone 13 128G已退出账号', _utf8mb4'iPhone 13 128G已退出账号，128GB版本，卖家声明交付前退出个人账户。适合日常通信、校园拍摄和移动应用测试。', _utf8mb4'https://placehold.co/600x400/png?text=Phone-iPhone13#PUBLIC-DEMO-COMMODITY-V1-a4ac21fbeab03739', _utf8mb4'八成新', 2380.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-3a9a1e2e301b84cb', _utf8mb4'数码家电类', _utf8mb4'Redmi K50 12G 256G安卓手机', _utf8mb4'Redmi K50 12G 256G安卓手机，12GB内存、256GB存储，双卡版本。适合安卓开发调试、备用机和校园日常使用。', _utf8mb4'https://placehold.co/600x400/png?text=Phone-K50#PUBLIC-DEMO-COMMODITY-V1-3a9a1e2e301b84cb', _utf8mb4'八五新', 980.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-61c81672b25b74cc', _utf8mb4'数码家电类', _utf8mb4'iPad 9 64G含原装笔记本保护套', _utf8mb4'iPad 9 64G含原装笔记本保护套，64GB Wi-Fi版，含保护套，不含一次性软件权益。适合电子教材、课堂笔记和文档批注。', _utf8mb4'https://placehold.co/600x400/png?text=Tablet-iPad9#PUBLIC-DEMO-COMMODITY-V1-61c81672b25b74cc', _utf8mb4'九成新', 1480.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-2ebfa42456a04d6c', _utf8mb4'数码家电类', _utf8mb4'TP-LINK XDR3010 WiFi6路由器', _utf8mb4'TP-LINK XDR3010 WiFi6路由器，AX3000级双频路由器，含匹配电源。适合宿舍或合租房多设备联网。', _utf8mb4'https://placehold.co/600x400/png?text=Router-WiFi6#PUBLIC-DEMO-COMMODITY-V1-2ebfa42456a04d6c', _utf8mb4'九成新', 145.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-2651df916c04bb91', _utf8mb4'数码家电类', _utf8mb4'USB-C双HDMI九合一扩展坞', _utf8mb4'USB-C双HDMI九合一扩展坞，含双HDMI、USB-A、读卡、网口和PD输入。适合轻薄本外接显示器、网线和实验设备。', _utf8mb4'https://placehold.co/600x400/png?text=Dock-9in1#PUBLIC-DEMO-COMMODITY-V1-2651df916c04bb91', _utf8mb4'八五新', 128.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-faa05599f6019015', _utf8mb4'数码家电类', _utf8mb4'绿联100W氮化镓三口充电器', _utf8mb4'绿联100W氮化镓三口充电器，双USB-C加USB-A，标称最高100W。适合替代大体积笔记本与手机充电器。', _utf8mb4'https://placehold.co/600x400/png?text=Charger-100W#PUBLIC-DEMO-COMMODITY-V1-faa05599f6019015', _utf8mb4'九成新', 168.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-5aec4e62da32c48c', _utf8mb4'数码家电类', _utf8mb4'奥睿科USB4 NVMe硬盘盒', _utf8mb4'奥睿科USB4 NVMe硬盘盒，M.2 NVMe硬盘盒，含数据线和散热垫。适合课程项目、虚拟机和大文件移动存储。', _utf8mb4'https://placehold.co/600x400/png?text=SSD-Enclosure#PUBLIC-DEMO-COMMODITY-V1-5aec4e62da32c48c', _utf8mb4'九五新', 220.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-4dead9c501056db7', _utf8mb4'数码家电类', _utf8mb4'西部数据4TB移动硬盘含收纳包', _utf8mb4'西部数据4TB移动硬盘含收纳包，2.5英寸USB移动机械硬盘，标称4TB。适合实验数据、课程资料和毕业设计多副本备份。', _utf8mb4'https://placehold.co/600x400/png?text=HDD-4TB#PUBLIC-DEMO-COMMODITY-V1-4dead9c501056db7', _utf8mb4'八五新', 460.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-cc024cb2b69f809d', _utf8mb4'数码家电类', _utf8mb4'罗技MX Master 3无线鼠标', _utf8mb4'罗技MX Master 3无线鼠标，蓝牙与接收器双连接，含接收器。适合长时间编程、设计和多设备切换。', _utf8mb4'https://placehold.co/600x400/png?text=Mouse-MX3#PUBLIC-DEMO-COMMODITY-V1-cc024cb2b69f809d', _utf8mb4'八成新', 285.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-58e3bfe791701193', _utf8mb4'数码家电类', _utf8mb4'便携USB逻辑分析仪16通道套装', _utf8mb4'便携USB逻辑分析仪16通道套装，16通道逻辑分析仪，含测试夹线。适合数字电路、嵌入式通信和协议调试。', _utf8mb4'https://placehold.co/600x400/png?text=Logic-Analyzer#PUBLIC-DEMO-COMMODITY-V1-58e3bfe791701193', _utf8mb4'九成新', 260.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-eb44a8e750d3a928', _utf8mb4'办公用品类', _utf8mb4'STM32F407开发板含ST-Link与数据线', _utf8mb4'STM32F407开发板含ST-Link与数据线，STM32F407核心开发板，含ST-Link下载器、USB线和排针。适合嵌入式、自动化与电子类课程实验。', _utf8mb4'https://placehold.co/600x400/png?text=STM32-F407#PUBLIC-DEMO-COMMODITY-V1-eb44a8e750d3a928', _utf8mb4'九成新', 135.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-8ffaa36d707100e1', _utf8mb4'办公用品类', _utf8mb4'ESP32 DevKitC无线开发板两块装', _utf8mb4'ESP32 DevKitC无线开发板两块装，ESP32开发板两块，含Type-C数据线。适合物联网、传感器联网和小型课程项目。', _utf8mb4'https://placehold.co/600x400/png?text=ESP32#PUBLIC-DEMO-COMMODITY-V1-8ffaa36d707100e1', _utf8mb4'九五新', 72.00, 3),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-a8d58163ab5b0f28', _utf8mb4'办公用品类', _utf8mb4'Raspberry Pi Pico入门实验套件', _utf8mb4'Raspberry Pi Pico入门实验套件，Pico主板、面包板、跳线、LED和常用电阻。适合C语言、MicroPython与基础硬件实验。', _utf8mb4'https://placehold.co/600x400/png?text=Pico-Kit#PUBLIC-DEMO-COMMODITY-V1-a8d58163ab5b0f28', _utf8mb4'九成新', 88.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-639de0089e25005c', _utf8mb4'办公用品类', _utf8mb4'优利德UT61E数字万用表含表笔', _utf8mb4'优利德UT61E数字万用表含表笔，自动量程万用表，含红黑表笔和收纳包。适合电路测量、电子工艺和维修排查。', _utf8mb4'https://placehold.co/600x400/png?text=Multimeter#PUBLIC-DEMO-COMMODITY-V1-639de0089e25005c', _utf8mb4'八五新', 230.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-3f8a2ae51dea199e', _utf8mb4'办公用品类', _utf8mb4'可调直流稳压电源30V5A', _utf8mb4'可调直流稳压电源30V5A，单路0至30V、0至5A台式电源，含输出线。适合电路实验、开发板供电和器件测试。', _utf8mb4'https://placehold.co/600x400/png?text=DC-Power#PUBLIC-DEMO-COMMODITY-V1-3f8a2ae51dea199e', _utf8mb4'八成新', 285.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-784d888f79d9248c', _utf8mb4'办公用品类', _utf8mb4'T12数显焊台含三只烙铁头', _utf8mb4'T12数显焊台含三只烙铁头，T12数显焊台、手柄、支架和三只烙铁头。适合电子工艺、焊接练习和开发板维修。', _utf8mb4'https://placehold.co/600x400/png?text=Solder-T12#PUBLIC-DEMO-COMMODITY-V1-784d888f79d9248c', _utf8mb4'八五新', 168.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-d2b8963b4cda284e', _utf8mb4'办公用品类', _utf8mb4'USB隔离串口模块TTL调试套装', _utf8mb4'USB隔离串口模块TTL调试套装，USB转TTL模块，支持常见电平切换，含杜邦线。适合单片机串口、日志输出和固件调试。', _utf8mb4'https://placehold.co/600x400/png?text=USB-TTL#PUBLIC-DEMO-COMMODITY-V1-d2b8963b4cda284e', _utf8mb4'九五新', 58.00, 4),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-5530cfdc0f2a6534', _utf8mb4'办公用品类', _utf8mb4'830孔面包板电子元件基础包', _utf8mb4'830孔面包板电子元件基础包，面包板、跳线、电阻电容、按键和LED组合。适合模电数电入门和临时电路搭建。', _utf8mb4'https://placehold.co/600x400/png?text=Breadboard#PUBLIC-DEMO-COMMODITY-V1-5530cfdc0f2a6534', _utf8mb4'九成新', 65.00, 3),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-7d56872c5032a7b6', _utf8mb4'办公用品类', _utf8mb4'TI-Nspire CX II图形计算器', _utf8mb4'TI-Nspire CX II图形计算器，彩屏图形计算器，含充电线和保护壳。适合明确允许使用图形计算器的课程和个人计算练习。', _utf8mb4'https://placehold.co/600x400/png?text=Calculator-TI#PUBLIC-DEMO-COMMODITY-V1-7d56872c5032a7b6', _utf8mb4'八五新', 620.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-909b13b3bfdf5e9e', _utf8mb4'办公用品类', _utf8mb4'兄弟HL-1218W黑白激光打印机', _utf8mb4'兄弟HL-1218W黑白激光打印机，无线黑白激光打印机，含电源线和剩余硒鼓。适合宿舍打印报告、作业和社团材料。', _utf8mb4'https://placehold.co/600x400/png?text=Printer-Laser#PUBLIC-DEMO-COMMODITY-V1-909b13b3bfdf5e9e', _utf8mb4'八成新', 390.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-aba28125910cbade', _utf8mb4'办公用品类', _utf8mb4'A4护眼阅读架可调角度金属款', _utf8mb4'A4护眼阅读架可调角度金属款，金属折叠阅读架，多档角度。适合教材阅读、抄写和桌面空间整理。', _utf8mb4'https://placehold.co/600x400/png?text=Book-Stand#PUBLIC-DEMO-COMMODITY-V1-aba28125910cbade', _utf8mb4'九五新', 48.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-3ef6b6a59b78caa8', _utf8mb4'办公用品类', _utf8mb4'工程制图工具套装含圆规三角板', _utf8mb4'工程制图工具套装含圆规三角板，圆规、三角板、比例尺、模板和自动铅笔。适合工程制图、手绘草图和实验记录。', _utf8mb4'https://placehold.co/600x400/png?text=Drawing-Kit#PUBLIC-DEMO-COMMODITY-V1-3ef6b6a59b78caa8', _utf8mb4'九成新', 42.00, 3),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-08087d42b9b48fcc', _utf8mb4'电器类', _utf8mb4'美的1.5L电热水壶带原配底座', _utf8mb4'美的1.5L电热水壶带原配底座，1.5L壶体与原配底座，铭牌可读。适合宿舍或租房日常烧水。', _utf8mb4'https://placehold.co/600x400/png?text=Kettle#PUBLIC-DEMO-COMMODITY-V1-08087d42b9b48fcc', _utf8mb4'八成新', 55.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-8f108f8a43db96df', _utf8mb4'电器类', _utf8mb4'德力西六位过载保护排插3米', _utf8mb4'德力西六位过载保护排插3米，六位插孔、3米线、带过载保护开关。适合宿舍桌面电脑与低功率设备集中供电。', _utf8mb4'https://placehold.co/600x400/png?text=Power-Strip#PUBLIC-DEMO-COMMODITY-V1-8f108f8a43db96df', _utf8mb4'九成新', 48.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-c4e1e4bbdd7dd89b', _utf8mb4'电器类', _utf8mb4'海尔50L单门小冰箱', _utf8mb4'海尔50L单门小冰箱，50L单门冷藏箱，含层架和电源线。适合租房或允许使用的宿舍空间存放密封食品。', _utf8mb4'https://placehold.co/600x400/png?text=Mini-Fridge#PUBLIC-DEMO-COMMODITY-V1-c4e1e4bbdd7dd89b', _utf8mb4'八成新', 350.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-7a4822a8faee323f', _utf8mb4'电器类', _utf8mb4'美的3L电饭煲原装内胆', _utf8mb4'美的3L电饭煲原装内胆，3L机械式电饭煲，原装内胆和电源线。适合租房做饭或符合规定的生活空间。', _utf8mb4'https://placehold.co/600x400/png?text=Rice-Cooker#PUBLIC-DEMO-COMMODITY-V1-7a4822a8faee323f', _utf8mb4'八五新', 95.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-c057d5ad1619bf60', _utf8mb4'电器类', _utf8mb4'小米落地扇2直流变频版', _utf8mb4'小米落地扇2直流变频版，直流落地扇，含底座、电源线和遥控功能。适合宿舍降温和夜间低噪使用。', _utf8mb4'https://placehold.co/600x400/png?text=Standing-Fan#PUBLIC-DEMO-COMMODITY-V1-c057d5ad1619bf60', _utf8mb4'八五新', 145.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-20583a4d613bfa7e', _utf8mb4'电器类', _utf8mb4'飞利浦HP8230吹风机', _utf8mb4'飞利浦HP8230吹风机，常规家用吹风机，线材完整。适合日常吹干头发，需遵守所在宿舍用电规定。', _utf8mb4'https://placehold.co/600x400/png?text=Hair-Dryer#PUBLIC-DEMO-COMMODITY-V1-20583a4d613bfa7e', _utf8mb4'八成新', 68.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-36c880925a800647', _utf8mb4'电器类', _utf8mb4'德尔玛除湿机12L家用款', _utf8mb4'德尔玛除湿机12L家用款，标称日除湿量12L，含水箱和电源线。适合潮湿房间、租房或符合规定的宿舍环境。', _utf8mb4'https://placehold.co/600x400/png?text=Dehumidifier#PUBLIC-DEMO-COMMODITY-V1-36c880925a800647', _utf8mb4'八成新', 420.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-678b4ceac590d7cb', _utf8mb4'电器类', _utf8mb4'格兰仕20L机械式微波炉', _utf8mb4'格兰仕20L机械式微波炉，20L机械旋钮微波炉，转盘与托环齐全。适合租房公共厨房或明确允许使用的场所。', _utf8mb4'https://placehold.co/600x400/png?text=Microwave#PUBLIC-DEMO-COMMODITY-V1-678b4ceac590d7cb', _utf8mb4'七成新', 210.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-7a956f4268ebe12d', _utf8mb4'电器类', _utf8mb4'荣事达1.2L电煮杯分体电源款', _utf8mb4'荣事达1.2L电煮杯分体电源款，1.2L电煮杯，分体电源线与盖子齐全。适合租房简餐或符合用电规定的生活空间。', _utf8mb4'https://placehold.co/600x400/png?text=Cooking-Cup#PUBLIC-DEMO-COMMODITY-V1-7a956f4268ebe12d', _utf8mb4'九成新', 75.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-17f173c4af41b31e', _utf8mb4'电器类', _utf8mb4'松下LED护眼台灯调光版', _utf8mb4'松下LED护眼台灯调光版，LED台灯，多档亮度与色温，含原配电源。适合宿舍阅读、绘图和夜间学习。', _utf8mb4'https://placehold.co/600x400/png?text=Desk-Lamp#PUBLIC-DEMO-COMMODITY-V1-17f173c4af41b31e', _utf8mb4'九成新', 120.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-0b40c57c19de904d', _utf8mb4'日常用品类', _utf8mb4'20英寸静音万向轮登机箱', _utf8mb4'20英寸静音万向轮登机箱，20英寸箱体，四组万向轮、拉杆和密码锁。适合假期出行、毕业搬宿舍和短途旅行。', _utf8mb4'https://placehold.co/600x400/png?text=Luggage-20#PUBLIC-DEMO-COMMODITY-V1-0b40c57c19de904d', _utf8mb4'八五新', 115.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-ba04008861670976', _utf8mb4'日常用品类', _utf8mb4'捷安特Escape 2城市通勤自行车', _utf8mb4'捷安特Escape 2城市通勤自行车，平把城市自行车，铝合金车架，多速传动。适合校园通勤和周边短途骑行。', _utf8mb4'https://placehold.co/600x400/png?text=Bike-City#PUBLIC-DEMO-COMMODITY-V1-ba04008861670976', _utf8mb4'七成新', 780.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-b5192677c078e815', _utf8mb4'日常用品类', _utf8mb4'折叠晾衣架双翼加厚款', _utf8mb4'折叠晾衣架双翼加厚款，可折叠双翼结构，金属管架。适合宿舍阳台、租房晾晒和毕业季转手。', _utf8mb4'https://placehold.co/600x400/png?text=Drying-Rack#PUBLIC-DEMO-COMMODITY-V1-b5192677c078e815', _utf8mb4'九成新', 58.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-c9fbcd8eb0571f33', _utf8mb4'日常用品类', _utf8mb4'三层带轮宿舍收纳推车', _utf8mb4'三层带轮宿舍收纳推车，三层金属网篮、四只脚轮。适合床边、桌旁和洗漱用品分类收纳。', _utf8mb4'https://placehold.co/600x400/png?text=Storage-Cart#PUBLIC-DEMO-COMMODITY-V1-c9fbcd8eb0571f33', _utf8mb4'九五新', 65.00, 2),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-da662d588cd024c2', _utf8mb4'日常用品类', _utf8mb4'人体工学网布电脑椅可升降', _utf8mb4'人体工学网布电脑椅可升降，网布靠背、升降气杆和扶手。适合宿舍或租房长时间学习与编程。', _utf8mb4'https://placehold.co/600x400/png?text=Office-Chair#PUBLIC-DEMO-COMMODITY-V1-da662d588cd024c2', _utf8mb4'八成新', 260.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-74c151aab6a978e8', _utf8mb4'日常用品类', _utf8mb4'北弧F80显示器气压支架', _utf8mb4'北弧F80显示器气压支架，桌夹式单屏支架，适配常见VESA孔位。适合宿舍桌面释放空间和调整屏幕高度。', _utf8mb4'https://placehold.co/600x400/png?text=Monitor-Arm#PUBLIC-DEMO-COMMODITY-V1-74c151aab6a978e8', _utf8mb4'九成新', 125.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-788c5eb7f7b6bf9b', _utf8mb4'日常用品类', _utf8mb4'宿舍床垫90×190厘米可折叠', _utf8mb4'宿舍床垫90×190厘米可折叠，90×190厘米折叠床垫，非贴身床品套件。适合宿舍床位或临时住宿。', _utf8mb4'https://placehold.co/600x400/png?text=Mattress#PUBLIC-DEMO-COMMODITY-V1-788c5eb7f7b6bf9b', _utf8mb4'八成新', 80.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-6b1ba2c51040f131', _utf8mb4'日常用品类', _utf8mb4'家用工具箱含螺丝刀扳手套装', _utf8mb4'家用工具箱含螺丝刀扳手套装，螺丝刀、内六角、活动扳手、卷尺和常用小工具。适合宿舍家具装配、自行车小调整和搬家拆装。', _utf8mb4'https://placehold.co/600x400/png?text=Toolbox#PUBLIC-DEMO-COMMODITY-V1-6b1ba2c51040f131', _utf8mb4'九成新', 98.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-ba6b285efddc6fb9', _utf8mb4'服装鞋帽类', _utf8mb4'ASICS Gel-Kayano 28跑鞋 42码', _utf8mb4'ASICS Gel-Kayano 28跑鞋 42码，标注42码，鞋面有使用痕迹。适合慢跑和体测训练。', _utf8mb4'https://placehold.co/600x400/png?text=Running-Shoes#PUBLIC-DEMO-COMMODITY-V1-ba6b285efddc6fb9', _utf8mb4'七成新', 165.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-2eaa6bc41b9b77e0', _utf8mb4'服装鞋帽类', _utf8mb4'迪卡侬MH500徒步鞋 41码', _utf8mb4'迪卡侬MH500徒步鞋 41码，标注41码，中帮徒步鞋。适合社团徒步和周末短途活动。', _utf8mb4'https://placehold.co/600x400/png?text=Hiking-Shoes#PUBLIC-DEMO-COMMODITY-V1-2eaa6bc41b9b77e0', _utf8mb4'八成新', 145.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-756554539c4fa50a', _utf8mb4'服装鞋帽类', _utf8mb4'未使用Giro Register骑行头盔 M码', _utf8mb4'未使用Giro Register骑行头盔 M码，M码骑行头盔，卖家描述未佩戴，包装与调节器齐全。适合校园骑行和通勤防护。', _utf8mb4'https://placehold.co/600x400/png?text=Helmet#PUBLIC-DEMO-COMMODITY-V1-756554539c4fa50a', _utf8mb4'全新', 180.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-50007fe03b017720', _utf8mb4'服装鞋帽类', _utf8mb4'Nike 25L双肩电脑包', _utf8mb4'Nike 25L双肩电脑包，约25L容量，带独立电脑夹层。适合携带笔记本、教材和日常通勤。', _utf8mb4'https://placehold.co/600x400/png?text=Backpack#PUBLIC-DEMO-COMMODITY-V1-50007fe03b017720', _utf8mb4'八五新', 110.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-0546f060814cec7f', _utf8mb4'服装鞋帽类', _utf8mb4'Columbia防水冲锋衣 L码', _utf8mb4'Columbia防水冲锋衣 L码，标注L码，带帽外层冲锋衣。适合雨天通勤、社团户外和旅行备用。', _utf8mb4'https://placehold.co/600x400/png?text=Jacket#PUBLIC-DEMO-COMMODITY-V1-0546f060814cec7f', _utf8mb4'八成新', 220.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-9d92902a23d31f7a', _utf8mb4'服装鞋帽类', _utf8mb4'尤尼克斯羽毛球鞋 43码', _utf8mb4'尤尼克斯羽毛球鞋 43码，标注43码，室内运动鞋。适合体育课、社团训练和球馆使用。', _utf8mb4'https://placehold.co/600x400/png?text=Badminton-Shoes#PUBLIC-DEMO-COMMODITY-V1-9d92902a23d31f7a', _utf8mb4'七成新', 135.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-591dbf960f133612', _utf8mb4'宠物用品类', _utf8mb4'中号可拆洗宠物航空箱', _utf8mb4'中号可拆洗宠物航空箱，中号硬壳航空箱，门锁和提手齐全。适合猫犬短途就医和搬运。', _utf8mb4'https://placehold.co/600x400/png?text=Pet-Carrier#PUBLIC-DEMO-COMMODITY-V1-591dbf960f133612', _utf8mb4'八五新', 95.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-bca85330b105c641', _utf8mb4'宠物用品类', _utf8mb4'45厘米前开门玻璃爬宠箱', _utf8mb4'45厘米前开门玻璃爬宠箱，45厘米玻璃箱体，前开门和通风网。适合爬宠环境搭建或临时隔离。', _utf8mb4'https://placehold.co/600x400/png?text=Terrarium#PUBLIC-DEMO-COMMODITY-V1-bca85330b105c641', _utf8mb4'八成新', 180.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-3510cf4d52ecb63b', _utf8mb4'宠物用品类', _utf8mb4'三层实木猫爬架可拆装', _utf8mb4'三层实木猫爬架可拆装，三层结构，含抓柱与平台，可拆装搬运。适合宿舍外租房或家庭猫咪活动。', _utf8mb4'https://placehold.co/600x400/png?text=Cat-Tree#PUBLIC-DEMO-COMMODITY-V1-3510cf4d52ecb63b', _utf8mb4'八成新', 150.00, 1),
(_utf8mb4'PUBLIC-DEMO-COMMODITY-V1-8591fb24ee6c37b5', _utf8mb4'宠物用品类', _utf8mb4'六片式金属宠物围栏', _utf8mb4'六片式金属宠物围栏，六片可组合金属围栏，含连接件。适合幼犬、小型宠物活动区和临时隔离。', _utf8mb4'https://placehold.co/600x400/png?text=Pet-Fence#PUBLIC-DEMO-COMMODITY-V1-8591fb24ee6c37b5', _utf8mb4'九成新', 120.00, 1);

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
     WHERE commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%') = 60
    AND (SELECT COUNT(DISTINCT commodityName) FROM commodity
         WHERE commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%') = 60
    AND NOT EXISTS (
        SELECT 1 FROM commodity
        WHERE commodityAvatar LIKE '%#PUBLIC-DEMO-COMMODITY-V1-%' OR commodityDescription LIKE '%资料条目标识：PUBLIC-DEMO-COMMODITY-V1-%'
          AND (adminId <> 2074697289959530497 OR isListed <> 1 OR commodityInventory <= 0
               OR price <= 0 OR isDelete <> 0)
    ),
    1, 0
);

DROP TEMPORARY TABLE post_aligned_commodity_rows;
DROP TEMPORARY TABLE post_aligned_commodity_guard;
COMMIT;
