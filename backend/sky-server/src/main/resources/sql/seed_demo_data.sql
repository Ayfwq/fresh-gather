SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE IF NOT EXISTS flash_sale_activity
(
    id              BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    activity_name   VARCHAR(64)    NOT NULL COMMENT '活动名称',
    dish_id         BIGINT         NOT NULL COMMENT '菜品ID',
    sale_price      DECIMAL(10, 2) NOT NULL COMMENT '特卖价',
    stock           INT            NOT NULL COMMENT '总库存',
    available_stock INT            NOT NULL COMMENT '剩余库存',
    limit_per_user  INT            NOT NULL DEFAULT 1 COMMENT '单用户限购次数',
    start_time      DATETIME       NOT NULL COMMENT '活动开始时间',
    end_time        DATETIME       NOT NULL COMMENT '活动结束时间',
    status          TINYINT        NOT NULL DEFAULT 0 COMMENT '状态：0禁用 1启用',
    create_time     DATETIME       NULL COMMENT '创建时间',
    update_time     DATETIME       NULL COMMENT '更新时间',
    create_user     BIGINT         NULL COMMENT '创建人',
    update_user     BIGINT         NULL COMMENT '更新人',
    KEY idx_dish_id (dish_id),
    KEY idx_status_time (status, start_time, end_time)
) COMMENT='特卖活动表';

CREATE TABLE IF NOT EXISTS flash_sale_order
(
    id          BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    activity_id BIGINT       NOT NULL COMMENT '活动ID',
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    order_id    BIGINT       NULL COMMENT '正式订单ID',
    request_id  VARCHAR(64)  NOT NULL COMMENT '抢购请求幂等ID',
    status      TINYINT      NOT NULL COMMENT '状态：1已创建待支付 2已支付 3已取消 4创建失败',
    fail_reason VARCHAR(255) NULL COMMENT '失败或取消原因',
    create_time DATETIME     NULL COMMENT '创建时间',
    update_time DATETIME     NULL COMMENT '更新时间',
    UNIQUE KEY uk_request_id (request_id),
    KEY idx_activity_user (activity_id, user_id),
    KEY idx_order_id (order_id)
) COMMENT='特卖抢购记录表';

TRUNCATE TABLE flash_sale_order;
TRUNCATE TABLE flash_sale_activity;
TRUNCATE TABLE order_detail;
TRUNCATE TABLE orders;
TRUNCATE TABLE address_book;
TRUNCATE TABLE user;
TRUNCATE TABLE setmeal_dish;
TRUNCATE TABLE setmeal;

SET FOREIGN_KEY_CHECKS = 1;

INSERT INTO user (openid, name, phone, sex, id_number, avatar, create_time)
VALUES ('wx_demo_1001', '林晓雨', '13800010001', '2', '110101199405180021', 'https://example.com/avatar/u01.png', '2026-04-02 09:12:00'),
       ('wx_demo_1002', '周景辰', '13800010002', '1', '110101199203110037', 'https://example.com/avatar/u02.png', '2026-04-02 10:26:00'),
       ('wx_demo_1003', '顾言希', '13800010003', '2', '110101199612090028', 'https://example.com/avatar/u03.png', '2026-04-03 11:08:00'),
       ('wx_demo_1004', '陈子墨', '13800010004', '1', '110101199011230019', 'https://example.com/avatar/u04.png', '2026-04-03 14:35:00'),
       ('wx_demo_1005', '宋知夏', '13800010005', '2', '110101199807160024', 'https://example.com/avatar/u05.png', '2026-04-04 09:40:00'),
       ('wx_demo_1006', '许一舟', '13800010006', '1', '110101199501050012', 'https://example.com/avatar/u06.png', '2026-04-04 16:15:00'),
       ('wx_demo_1007', '沈念', '13800010007', '2', '110101199702140029', 'https://example.com/avatar/u07.png', '2026-04-05 13:18:00'),
       ('wx_demo_1008', '韩泽', '13800010008', '1', '110101199309190031', 'https://example.com/avatar/u08.png', '2026-04-05 17:42:00'),
       ('wx_demo_1009', '程可可', '13800010009', '2', '110101199911300046', 'https://example.com/avatar/u09.png', '2026-04-06 10:02:00'),
       ('wx_demo_1010', '陆知行', '13800010010', '1', '110101199111210015', 'https://example.com/avatar/u10.png', '2026-04-06 11:56:00'),
       ('wx_demo_1011', '乔暖', '13800010011', '2', '110101199605260044', 'https://example.com/avatar/u11.png', '2026-04-07 08:45:00'),
       ('wx_demo_1012', '江临', '13800010012', '1', '110101199401010053', 'https://example.com/avatar/u12.png', '2026-04-07 19:24:00');

INSERT INTO address_book (user_id, consignee, sex, phone, province_code, province_name, city_code, city_name, district_code, district_name, detail, label, is_default)
VALUES (1, '林晓雨', '2', '13800010001', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '望京SOHO T2 18层', '公司', 1),
       (2, '周景辰', '1', '13800010002', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '上地东路1号院 3号楼', '家', 1),
       (3, '顾言希', '2', '13800010003', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '酒仙桥798艺术区 7号', '家', 1),
       (4, '陈子墨', '1', '13800010004', '110000', '北京市', '110100', '北京市', '110106', '丰台区', '科技园总部基地 11号楼', '公司', 1),
       (5, '宋知夏', '2', '13800010005', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '中关村软件园二期 8号楼', '公司', 1),
       (6, '许一舟', '1', '13800010006', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '三里屯太古里南区 5号', '家', 1),
       (7, '沈念', '2', '13800010007', '110000', '北京市', '110100', '北京市', '110101', '东城区', '崇文门新世界公寓 12层', '家', 1),
       (8, '韩泽', '1', '13800010008', '110000', '北京市', '110100', '北京市', '110107', '石景山区', '万达广场写字楼 A座', '公司', 1),
       (9, '程可可', '2', '13800010009', '110000', '北京市', '110100', '北京市', '110102', '西城区', '金融街中心 8层', '公司', 1),
       (10, '陆知行', '1', '13800010010', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '大望路华贸中心 21层', '公司', 1),
       (11, '乔暖', '2', '13800010011', '110000', '北京市', '110100', '北京市', '110108', '海淀区', '五道口华清嘉园 2号楼', '家', 1),
       (12, '江临', '1', '13800010012', '110000', '北京市', '110100', '北京市', '110105', '朝阳区', '双井富力城 B区 6号楼', '家', 1);

INSERT INTO setmeal (category_id, name, price, status, description, image, create_time, update_time, create_user, update_user)
VALUES (13, '双人酸菜鱼套餐', 108.00, 1, '老坛酸菜鱼+米饭*2+平菇豆腐汤', 'https://example.com/setmeal/sm1.png', NOW(), NOW(), 1, 1),
       (13, '牛蛙欢聚套餐', 128.00, 1, '香锅牛蛙+蒜蓉娃娃菜+王老吉', 'https://example.com/setmeal/sm2.png', NOW(), NOW(), 1, 1),
       (15, '工作日轻享套餐', 42.00, 1, '蜀味水煮草鱼+米饭+北冰洋', 'https://example.com/setmeal/sm3.png', NOW(), NOW(), 1, 1);

SET @sm1 := (SELECT id FROM setmeal WHERE name = '双人酸菜鱼套餐' LIMIT 1);
SET @sm2 := (SELECT id FROM setmeal WHERE name = '牛蛙欢聚套餐' LIMIT 1);
SET @sm3 := (SELECT id FROM setmeal WHERE name = '工作日轻享套餐' LIMIT 1);

INSERT INTO setmeal_dish (setmeal_id, dish_id, name, price, copies)
VALUES (@sm1, 51, '老坛酸菜鱼', 56.00, 1),
       (@sm1, 49, '米饭', 2.00, 2),
       (@sm1, 69, '平菇豆腐汤', 6.00, 1),
       (@sm2, 63, '香锅牛蛙', 88.00, 1),
       (@sm2, 55, '蒜蓉娃娃菜', 18.00, 1),
       (@sm2, 46, '王老吉', 6.00, 1),
       (@sm3, 53, '蜀味水煮草鱼', 38.00, 1),
       (@sm3, 49, '米饭', 2.00, 1),
       (@sm3, 47, '北冰洋', 4.00, 1);

DROP PROCEDURE IF EXISTS seed_demo_orders;
DELIMITER $$
CREATE PROCEDURE seed_demo_orders()
BEGIN
    DECLARE v_day DATE DEFAULT '2026-04-02';
    DECLARE v_end DATE DEFAULT '2026-04-16';
    DECLARE v_completed_count INT;
    DECLARE v_cancelled_count INT;
    DECLARE v_i INT;
    DECLARE v_user_id BIGINT;
    DECLARE v_address_id BIGINT;
    DECLARE v_order_time DATETIME;
    DECLARE v_checkout_time DATETIME;
    DECLARE v_delivery_time DATETIME;
    DECLARE v_order_id BIGINT;
    DECLARE v_order_no VARCHAR(64);
    DECLARE v_main_dish_id BIGINT;
    DECLARE v_main_price DECIMAL(10, 2);
    DECLARE v_side_dish_id BIGINT;
    DECLARE v_side_price DECIMAL(10, 2);
    DECLARE v_drink_dish_id BIGINT;
    DECLARE v_drink_price DECIMAL(10, 2);
    DECLARE v_amount DECIMAL(10, 2);
    DECLARE v_use_setmeal TINYINT DEFAULT 0;
    DECLARE v_setmeal_id BIGINT;
    DECLARE v_setmeal_price DECIMAL(10, 2);
    DECLARE v_reason VARCHAR(128);

    WHILE v_day <= v_end DO
        SET v_completed_count = CASE
                                    WHEN v_day = '2026-04-10' THEN 9
                                    WHEN v_day = '2026-04-15' THEN 18
                                    WHEN v_day = '2026-04-16' THEN 11
                                    WHEN DAYOFWEEK(v_day) IN (1, 7) THEN 20
                                    ELSE 13
            END;

        SET v_cancelled_count = CASE
                                    WHEN v_day = '2026-04-10' THEN 5
                                    WHEN v_day = '2026-04-16' THEN 4
                                    WHEN DAYOFWEEK(v_day) IN (1, 7) THEN 2
                                    ELSE 1
            END;

        SET v_i = 1;
        WHILE v_i <= v_completed_count DO
            SET v_user_id = ((DAY(v_day) + v_i - 1) MOD 12) + 1;
            SET v_address_id = v_user_id;
            SET v_order_time = DATE_ADD(CONCAT(v_day, ' ', CASE WHEN MOD(v_i, 2) = 0 THEN '11:10:00' ELSE '17:20:00' END),
                                        INTERVAL MOD(v_i * 11, 50) MINUTE);
            SET v_checkout_time = DATE_ADD(v_order_time, INTERVAL 6 MINUTE);
            SET v_delivery_time = DATE_ADD(v_order_time, INTERVAL 48 + MOD(v_i * 3, 22) MINUTE);
            SET v_order_no = CONCAT(DATE_FORMAT(v_order_time, '%Y%m%d%H%i%s'), LPAD(v_user_id, 3, '0'), LPAD(v_i, 2, '0'));
            SET v_use_setmeal = CASE
                                    WHEN v_day NOT IN ('2026-04-10', '2026-04-15', '2026-04-16') AND MOD(v_i, 6) = 0 THEN 1
                                    ELSE 0
                END;

            IF v_use_setmeal = 1 THEN
                SET v_setmeal_id = CASE MOD(v_i, 3)
                                       WHEN 0 THEN @sm1
                                       WHEN 1 THEN @sm2
                                       ELSE @sm3
                    END;
                SET v_setmeal_price = CASE MOD(v_i, 3)
                                          WHEN 0 THEN 108.00
                                          WHEN 1 THEN 128.00
                                          ELSE 42.00
                    END;
                SET v_drink_dish_id = CASE WHEN MOD(v_i, 2) = 0 THEN 46 ELSE 47 END;
                SET v_drink_price = CASE WHEN MOD(v_i, 2) = 0 THEN 6.00 ELSE 4.00 END;
                SET v_amount = v_setmeal_price + CASE WHEN MOD(v_i, 3) = 0 THEN v_drink_price ELSE 0 END;

                INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount,
                                    remark, phone, address, user_name, consignee, estimated_delivery_time, delivery_status, delivery_time,
                                    pack_amount, tableware_number, tableware_status)
                SELECT v_order_no, 5, u.id, v_address_id, v_order_time, v_checkout_time, 1, 1, v_amount,
                       CASE
                           WHEN v_day = '2026-04-15' THEN '轻量特惠套餐，拉高订单量'
                           WHEN DAYOFWEEK(v_day) IN (1, 7) THEN '周末家庭聚餐'
                           ELSE '系统生成示例订单'
                           END,
                       a.phone, a.detail, u.name, a.consignee, DATE_ADD(v_order_time, INTERVAL 55 MINUTE), 1, v_delivery_time, 2, 2, 1
                FROM user u
                         INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1
                WHERE u.id = v_user_id;

                SET v_order_id = LAST_INSERT_ID();

                INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                SELECT s.name, s.image, v_order_id, NULL, s.id, NULL, 1, s.price
                FROM setmeal s
                WHERE s.id = v_setmeal_id;

                IF MOD(v_i, 3) = 0 THEN
                    INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                    SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
                    FROM dish d
                    WHERE d.id = v_drink_dish_id;
                END IF;
            ELSE
                IF v_day IN ('2026-04-15', '2026-04-16') THEN
                    SET v_main_dish_id = CASE MOD(v_i, 7)
                                             WHEN 0 THEN 53
                                             WHEN 1 THEN 54
                                             WHEN 2 THEN 55
                                             WHEN 3 THEN 56
                                             WHEN 4 THEN 57
                                             WHEN 5 THEN 60
                                             ELSE 69
                        END;
                    SET v_main_price = CASE MOD(v_i, 7)
                                           WHEN 0 THEN 38.00
                                           WHEN 1 THEN 18.00
                                           WHEN 2 THEN 18.00
                                           WHEN 3 THEN 18.00
                                           WHEN 4 THEN 18.00
                                           WHEN 5 THEN 58.00
                                           ELSE 6.00
                        END;
                ELSE
                    SET v_main_dish_id = CASE MOD(v_i, 8)
                                             WHEN 0 THEN 65
                                             WHEN 1 THEN 51
                                             WHEN 2 THEN 62
                                             WHEN 3 THEN 58
                                             WHEN 4 THEN 60
                                             WHEN 5 THEN 63
                                             WHEN 6 THEN 53
                                             ELSE 66
                        END;
                    SET v_main_price = CASE MOD(v_i, 8)
                                           WHEN 0 THEN 68.00
                                           WHEN 1 THEN 56.00
                                           WHEN 2 THEN 88.00
                                           WHEN 3 THEN 98.00
                                           WHEN 4 THEN 58.00
                                           WHEN 5 THEN 88.00
                                           WHEN 6 THEN 38.00
                                           ELSE 119.00
                        END;
                END IF;

                SET v_side_dish_id = CASE WHEN MOD(v_i, 4) IN (0, 1) THEN 49 ELSE 50 END;
                SET v_side_price = CASE WHEN v_side_dish_id = 49 THEN 2.00 ELSE 1.00 END;
                SET v_drink_dish_id = CASE WHEN MOD(v_i, 2) = 0 THEN 47 ELSE 46 END;
                SET v_drink_price = CASE WHEN v_drink_dish_id = 47 THEN 4.00 ELSE 6.00 END;
                SET v_amount = v_main_price + v_side_price + CASE WHEN MOD(v_i, 3) = 0 THEN v_drink_price ELSE 0 END;

                INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount,
                                    remark, phone, address, user_name, consignee, estimated_delivery_time, delivery_status, delivery_time,
                                    pack_amount, tableware_number, tableware_status)
                SELECT v_order_no, 5, u.id, v_address_id, v_order_time, v_checkout_time, 1, 1, v_amount,
                       CASE
                           WHEN v_day = '2026-04-10' THEN '暴雨天高峰，订单量下滑'
                           WHEN v_day = '2026-04-15' THEN '特惠菜品带动，客单价下降'
                           WHEN v_day = '2026-04-16' THEN '活动尾声，流量回落'
                           WHEN DAYOFWEEK(v_day) IN (1, 7) THEN '周末聚餐高峰'
                           ELSE '系统生成示例订单'
                           END,
                       a.phone, a.detail, u.name, a.consignee, DATE_ADD(v_order_time, INTERVAL 50 MINUTE), 1, v_delivery_time, 2, 2, 1
                FROM user u
                         INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1
                WHERE u.id = v_user_id;

                SET v_order_id = LAST_INSERT_ID();

                INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
                FROM dish d
                WHERE d.id = v_main_dish_id;

                INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
                FROM dish d
                WHERE d.id = v_side_dish_id;

                IF MOD(v_i, 3) = 0 THEN
                    INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                    SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
                    FROM dish d
                    WHERE d.id = v_drink_dish_id;
                END IF;
            END IF;

            SET v_i = v_i + 1;
        END WHILE;

        SET v_i = 1;
        WHILE v_i <= v_cancelled_count DO
            SET v_user_id = ((DAY(v_day) + v_i + 4) MOD 12) + 1;
            SET v_address_id = v_user_id;
            SET v_order_time = DATE_ADD(CONCAT(v_day, ' 18:10:00'), INTERVAL MOD(v_i * 13, 45) MINUTE);
            SET v_order_no = CONCAT('C', DATE_FORMAT(v_order_time, '%Y%m%d%H%i%s'), LPAD(v_user_id, 3, '0'), LPAD(v_i, 2, '0'));
            SET v_main_dish_id = CASE MOD(v_i, 3)
                                     WHEN 0 THEN 62
                                     WHEN 1 THEN 63
                                     ELSE 65
                END;
            SET v_main_price = CASE MOD(v_i, 3)
                                   WHEN 0 THEN 88.00
                                   WHEN 1 THEN 88.00
                                   ELSE 68.00
                END;
            SET v_side_dish_id = 49;
            SET v_side_price = 2.00;
            SET v_drink_dish_id = 46;
            SET v_drink_price = 6.00;
            SET v_amount = v_main_price + v_side_price + CASE WHEN MOD(v_i, 2) = 0 THEN v_drink_price ELSE 0 END;
            SET v_reason = CASE
                               WHEN v_day = '2026-04-10' THEN '暴雨配送延迟，用户申请退款'
                               WHEN v_day = '2026-04-16' THEN '活动高峰出餐慢，用户取消'
                               ELSE '用户临时取消'
                END;

            INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount,
                                remark, phone, address, user_name, consignee, cancel_reason, cancel_time, estimated_delivery_time,
                                delivery_status, pack_amount, tableware_number, tableware_status)
            SELECT v_order_no, 6, u.id, v_address_id, v_order_time,
                   CASE WHEN MOD(v_i, 2) = 0 THEN DATE_ADD(v_order_time, INTERVAL 5 MINUTE) ELSE NULL END,
                   1, CASE WHEN MOD(v_i, 2) = 0 THEN 2 ELSE 0 END, v_amount,
                   '系统生成取消订单', a.phone, a.detail, u.name, a.consignee, v_reason,
                   DATE_ADD(v_order_time, INTERVAL 15 MINUTE), DATE_ADD(v_order_time, INTERVAL 55 MINUTE), 1, 2, 2, 1
            FROM user u
                     INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1
            WHERE u.id = v_user_id;

            SET v_order_id = LAST_INSERT_ID();

            INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
            SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
            FROM dish d
            WHERE d.id = v_main_dish_id;

            INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
            SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
            FROM dish d
            WHERE d.id = v_side_dish_id;

            IF MOD(v_i, 2) = 0 THEN
                INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
                SELECT d.name, d.image, v_order_id, d.id, NULL, NULL, 1, d.price
                FROM dish d
                WHERE d.id = v_drink_dish_id;
            END IF;

            SET v_i = v_i + 1;
        END WHILE;

        SET v_day = DATE_ADD(v_day, INTERVAL 1 DAY);
    END WHILE;

    INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount, remark, phone, address, user_name, consignee,
                        estimated_delivery_time, delivery_status, pack_amount, tableware_number, tableware_status)
    SELECT 'NOW20260417113000001', 2, 3, 3, '2026-04-17 11:30:00', '2026-04-17 11:36:00', 1, 1, 91.00,
           '今日午市待接单示例', a.phone, a.detail, u.name, a.consignee, '2026-04-17 12:20:00', 1, 2, 2, 1
    FROM user u INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1 WHERE u.id = 3;
    SET @today_order_1 := LAST_INSERT_ID();
    INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
    VALUES ('香锅牛蛙', NULL, @today_order_1, 63, NULL, NULL, 1, 88.00),
           ('米饭', NULL, @today_order_1, 49, NULL, NULL, 1, 2.00),
           ('北冰洋', NULL, @today_order_1, 47, NULL, NULL, 1, 4.00);

    INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount, remark, phone, address, user_name, consignee,
                        estimated_delivery_time, delivery_status, pack_amount, tableware_number, tableware_status)
    SELECT 'NOW20260417174500002', 3, 6, 6, '2026-04-17 17:45:00', '2026-04-17 17:50:00', 1, 1, 76.00,
           '今日晚市已接单示例', a.phone, a.detail, u.name, a.consignee, '2026-04-17 18:35:00', 1, 2, 2, 1
    FROM user u INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1 WHERE u.id = 6;
    SET @today_order_2 := LAST_INSERT_ID();
    INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
    VALUES ('草鱼2斤', NULL, @today_order_2, 65, NULL, NULL, 1, 68.00),
           ('米饭', NULL, @today_order_2, 49, NULL, NULL, 1, 2.00),
           ('鸡蛋汤', NULL, @today_order_2, 68, NULL, NULL, 1, 4.00);

    INSERT INTO orders (number, status, user_id, address_book_id, order_time, checkout_time, pay_method, pay_status, amount, remark, phone, address, user_name, consignee,
                        estimated_delivery_time, delivery_status, delivery_time, pack_amount, tableware_number, tableware_status)
    SELECT 'NOW20260417190500003', 4, 9, 9, '2026-04-17 19:05:00', '2026-04-17 19:09:00', 1, 1, 96.00,
           '今日晚市派送中示例', a.phone, a.detail, u.name, a.consignee, '2026-04-17 19:55:00', 1, '2026-04-17 19:52:00', 2, 2, 1
    FROM user u INNER JOIN address_book a ON a.user_id = u.id AND a.is_default = 1 WHERE u.id = 9;
    SET @today_order_3 := LAST_INSERT_ID();
    INSERT INTO order_detail (name, image, order_id, dish_id, setmeal_id, dish_flavor, number, amount)
    VALUES ('金汤酸菜牛蛙', NULL, @today_order_3, 62, NULL, NULL, 1, 88.00),
           ('米饭', NULL, @today_order_3, 49, NULL, NULL, 1, 2.00),
           ('鸡蛋汤', NULL, @today_order_3, 68, NULL, NULL, 1, 4.00);
END$$
DELIMITER ;

CALL seed_demo_orders();
DROP PROCEDURE IF EXISTS seed_demo_orders;

INSERT INTO flash_sale_activity (activity_name, dish_id, sale_price, stock, available_stock, limit_per_user, start_time, end_time, status, create_time, update_time, create_user, update_user)
VALUES ('北冰洋 1 元限时抢', 47, 1.00, 30, 30, 1, '2026-04-17 10:00:00', '2026-04-17 23:00:00', 1, NOW(), NOW(), 1, 1),
       ('午市轻享特卖日', 54, 9.90, 40, 37, 1, '2026-04-15 10:30:00', '2026-04-15 14:00:00', 0, NOW(), NOW(), 1, 1);

SET @flash_sale_history := (SELECT id FROM flash_sale_activity WHERE activity_name = '午市轻享特卖日' LIMIT 1);

INSERT INTO flash_sale_order (activity_id, user_id, order_id, request_id, status, fail_reason, create_time, update_time)
SELECT @flash_sale_history, user_id, id, CONCAT('seed-fs-', id), 2, NULL, order_time, checkout_time
FROM orders
WHERE DATE(order_time) = '2026-04-15'
  AND status = 5
ORDER BY id
LIMIT 2;

INSERT INTO flash_sale_order (activity_id, user_id, order_id, request_id, status, fail_reason, create_time, update_time)
SELECT @flash_sale_history, user_id, id, CONCAT('seed-fs-cancel-', id), 3, '活动高峰出餐慢，用户取消', order_time, cancel_time
FROM orders
WHERE DATE(order_time) = '2026-04-16'
  AND status = 6
ORDER BY id
LIMIT 1;

UPDATE flash_sale_activity
SET available_stock = stock - (SELECT COUNT(*) FROM flash_sale_order WHERE activity_id = @flash_sale_history)
WHERE id = @flash_sale_history;

