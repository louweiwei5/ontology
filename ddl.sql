-- 产品类别表
CREATE TABLE product_category (
    id INT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,   -- 对应 ex:ProductCategory 的本地名称或标识
    label VARCHAR(200)                   -- skos:prefLabel
);

-- 属性定义表（全局唯一）
CREATE TABLE attribute_definition (
    id INT PRIMARY KEY,
    attr_name VARCHAR(100) NOT NULL UNIQUE,  -- ex:attrName
    data_type VARCHAR(20) NOT NULL CHECK (data_type IN ('string','number','boolean')),
    unit VARCHAR(50),                        -- ex:attrUnit，可为空
    label VARCHAR(200),
    synonyms TEXT                            -- JSON数组，存储 skos:altLabel 同义词列表
);

-- 类别-属性关联表（多对多）
CREATE TABLE category_attribute (
    id INT PRIMARY KEY,
    category_id INT NOT NULL REFERENCES product_category(id),
    attr_def_id INT NOT NULL REFERENCES attribute_definition(id),
    UNIQUE (category_id, attr_def_id)        -- 保证不重复关联
);

-- 产品实例表
CREATE TABLE product (
    id INT PRIMARY KEY,
    category_id INT NOT NULL REFERENCES product_category(id),
    name VARCHAR(200) NOT NULL,              -- skos:prefLabel 产品型号
    description TEXT
);

-- 属性值表（EAV 模型）
CREATE TABLE attribute_value (
    id INT PRIMARY KEY,
    product_id INT NOT NULL REFERENCES product(id),
    attr_def_id INT NOT NULL REFERENCES attribute_definition(id),
    value_string VARCHAR(500),               -- 字符串值
    value_numeric DOUBLE,                    -- 数值
    value_unit VARCHAR(50),                  -- 实际值的单位
    -- 约束：根据 attribute_definition.data_type 校验非空列
    UNIQUE (product_id, attr_def_id)         -- 一个产品同一属性只有一个值
);