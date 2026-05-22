-- 插入产品类别数据
INSERT INTO product_category (id, name, label) VALUES
(1, 'front_camera', '前端摄像头'),
(2, 'back_nvr', '后端NVR');

-- 插入属性定义数据
INSERT INTO attribute_definition (id, attr_name, data_type, unit, label, synonyms) VALUES
-- 摄像头通用属性
(1, 'resolution', 'string', NULL, '分辨率', '["图像分辨率", "视频分辨率"]'),
(2, 'sensor_type', 'string', NULL, '传感器类型', '["感光元件", "图像传感器"]'),
(3, 'lens_focal_length', 'number', 'mm', '镜头焦距', '["焦距", "镜头参数"]'),
(4, 'night_vision_distance', 'number', '米', '夜视距离', '["红外距离", "夜间可视距离"]'),
(5, 'weather_resistance', 'string', NULL, '防护等级', '["防水等级", "防尘等级"]'),
(6, 'power_supply', 'string', NULL, '供电方式', '["电源类型", "供电电压"]'),

-- NVR专用属性
(7, 'channel_capacity', 'number', '路', '通道容量', '["支持通道数", "最大接入路数"]'),
(8, 'hard_drive_bays', 'number', '个', '硬盘盘位', '["硬盘槽位", "存储盘位"]'),
(9, 'max_storage_capacity', 'number', 'TB', '最大存储容量', '["存储上限", "硬盘容量"]'),
(10, 'video_compression', 'string', NULL, '视频压缩格式', '["编码格式", "压缩标准"]'),
(11, 'network_interface', 'string', NULL, '网络接口', '["网口类型", "网络连接"]'),
(12, 'poe_support', 'boolean', NULL, 'POE支持', '["以太网供电", "POE功能"]');

-- 插入类别-属性关联
INSERT INTO category_attribute (id, category_id, attr_def_id) VALUES
-- 摄像头属性关联
(1, 1, 1), (2, 1, 2), (3, 1, 3), (4, 1, 4), (5, 1, 5), (6, 1, 6),
-- NVR属性关联
(7, 2, 7), (8, 2, 8), (9, 2, 9), (10, 2, 10), (11, 2, 11), (12, 2, 12);

-- 插入产品实例数据
INSERT INTO product (id, category_id, name, description) VALUES
-- 前端摄像头产品
(1, 1, 'DS-2CD2346G2-I', '400万像素全彩警戒筒型网络摄像机'),
(2, 1, 'DS-2CD2386G2-IU', '800万像素超低照度筒型网络摄像机'),
(3, 1, 'DS-2DE4425IW-DE', '400万像素红外网络高速球机'),

-- 后端NVR产品
(4, 2, 'DS-7608NXI-I2/8P', '8路POE网络录像机，支持智能分析'),
(5, 2, 'DS-7716NXI-I4/16P', '16路4K网络录像机，4盘位'),
(6, 2, 'DS-9632NXI-I8', '32路超高清网络录像机，8盘位');

-- 插入摄像头属性值
INSERT INTO attribute_value (id, product_id, attr_def_id, value_string, value_numeric, value_unit) VALUES
-- DS-2CD2346G2-I 属性值
(1, 1, 1, '2560×1440', NULL, NULL),
(2, 1, 2, 'CMOS', NULL, NULL),
(3, 1, 3, NULL, 2.8, 'mm'),
(4, 1, 4, NULL, 30, '米'),
(5, 1, 5, 'IP67', NULL, NULL),
(6, 1, 6, 'DC12V/POE', NULL, NULL),

-- DS-2CD2386G2-IU 属性值
(7, 2, 1, '3840×2160', NULL, NULL),
(8, 2, 2, 'CMOS', NULL, NULL),
(9, 2, 3, NULL, 4, 'mm'),
(10, 2, 4, NULL, 40, '米'),
(11, 2, 5, 'IP67', NULL, NULL),
(12, 2, 6, 'DC12V/POE', NULL, NULL),

-- DS-2DE4425IW-DE 属性值
(13, 3, 1, '2560×1440', NULL, NULL),
(14, 3, 2, 'CMOS', NULL, NULL),
(15, 3, 3, NULL, 25, 'mm'),
(16, 3, 4, NULL, 100, '米'),
(17, 3, 5, 'IP66', NULL, NULL),
(18, 3, 6, 'AC24V', NULL, NULL);

-- 插入NVR属性值
INSERT INTO attribute_value (id, product_id, attr_def_id, value_string, value_numeric, value_unit) VALUES
-- DS-7608NXI-I2/8P 属性值
(19, 4, 7, NULL, 8, '路'),
(20, 4, 8, NULL, 2, '个'),
(21, 4, 9, NULL, 24, 'TB'),
(22, 4, 10, 'H.265+/H.265/H.264+', NULL, NULL),
(23, 4, 11, '10M/100M/1000M自适应', NULL, NULL),
(24, 4, 12, 'true', NULL, NULL),

-- DS-7716NXI-I4/16P 属性值
(25, 5, 7, NULL, 16, '路'),
(26, 5, 8, NULL, 4, '个'),
(27, 5, 9, NULL, 48, 'TB'),
(28, 5, 10, 'H.265+/H.265/H.264+', NULL, NULL),
(29, 5, 11, '双千兆网口', NULL, NULL),
(30, 5, 12, 'true', NULL, NULL),

-- DS-9632NXI-I8 属性值
(31, 6, 7, NULL, 32, '路'),
(32, 6, 8, NULL, 8, '个'),
(33, 6, 9, NULL, 96, 'TB'),
(34, 6, 10, 'H.265+/H.265/H.264+', NULL, NULL),
(35, 6, 11, '双千兆网口', NULL, NULL),
(36, 6, 12, 'false', NULL, NULL);

-- 新增7种摄像头产品实例
INSERT INTO product (id, category_id, name, description) VALUES
(7, 1, 'DS-2CD2047G2-L', '400万像素全彩警戒枪型网络摄像机'),
(8, 1, 'DS-2CD2087G2-L', '800万像素超低照度枪型网络摄像机'),
(9, 1, 'DS-2CD2T47G2-L', '400万像素全彩警戒半球网络摄像机'),
(10, 1, 'DS-2CD2T87G2-L', '800万像素超低照度半球网络摄像机'),
(11, 1, 'DS-2CD7A26G0/P-IZS', '200万像素全彩警戒筒型网络摄像机'),
(12, 1, 'DS-2CD7A28G0/P-IZS', '800万像素全彩警戒筒型网络摄像机'),
(13, 1, 'DS-2DF8442IXS-AELW', '400万像素红外网络高速球机');

-- 新增摄像头属性值
INSERT INTO attribute_value (id, product_id, attr_def_id, value_string, value_numeric, value_unit) VALUES
-- DS-2CD2047G2-L 属性值 (枪型摄像机)
(37, 7, 1, '2560×1440', NULL, NULL),
(38, 7, 2, 'CMOS', NULL, NULL),
(39, 7, 3, NULL, 2.8, 'mm'),
(40, 7, 4, NULL, 30, '米'),
(41, 7, 5, 'IP67', NULL, NULL),
(42, 7, 6, 'DC12V/POE', NULL, NULL),

-- DS-2CD2087G2-L 属性值 (枪型摄像机)
(43, 8, 1, '3840×2160', NULL, NULL),
(44, 8, 2, 'CMOS', NULL, NULL),
(45, 8, 3, NULL, 4, 'mm'),
(46, 8, 4, NULL, 40, '米'),
(47, 8, 5, 'IP67', NULL, NULL),
(48, 8, 6, 'DC12V/POE', NULL, NULL),

-- DS-2CD2T47G2-L 属性值 (半球摄像机)
(49, 9, 1, '2560×1440', NULL, NULL),
(50, 9, 2, 'CMOS', NULL, NULL),
(51, 9, 3, NULL, 2.8, 'mm'),
(52, 9, 4, NULL, 30, '米'),
(53, 9, 5, 'IP67', NULL, NULL),
(54, 9, 6, 'DC12V/POE', NULL, NULL),

-- DS-2CD2T87G2-L 属性值 (半球摄像机)
(55, 10, 1, '3840×2160', NULL, NULL),
(56, 10, 2, 'CMOS', NULL, NULL),
(57, 10, 3, NULL, 4, 'mm'),
(58, 10, 4, NULL, 40, '米'),
(59, 10, 5, 'IP67', NULL, NULL),
(60, 10, 6, 'DC12V/POE', NULL, NULL),

-- DS-2CD7A26G0/P-IZS 属性值 (经济型筒机)
(61, 11, 1, '1920×1080', NULL, NULL),
(62, 11, 2, 'CMOS', NULL, NULL),
(63, 11, 3, NULL, 2.8, 'mm'),
(64, 11, 4, NULL, 20, '米'),
(65, 11, 5, 'IP66', NULL, NULL),
(66, 11, 6, 'DC12V', NULL, NULL),

-- DS-2CD7A28G0/P-IZS 属性值 (经济型筒机)
(67, 12, 1, '3840×2160', NULL, NULL),
(68, 12, 2, 'CMOS', NULL, NULL),
(69, 12, 3, NULL, 4, 'mm'),
(70, 12, 4, NULL, 30, '米'),
(71, 12, 5, 'IP66', NULL, NULL),
(72, 12, 6, 'DC12V/POE', NULL, NULL),

-- DS-2DF8442IXS-AELW 属性值 (高速球机)
(73, 13, 1, '2688×1520', NULL, NULL),
(74, 13, 2, 'CMOS', NULL, NULL),
(75, 13, 3, NULL, 42, 'mm'),
(76, 13, 4, NULL, 150, '米'),
(77, 13, 5, 'IP67', NULL, NULL),
(78, 13, 6, 'AC24V/POE++', NULL, NULL);

