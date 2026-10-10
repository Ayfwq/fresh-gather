package com.sky.mapper;

import com.sky.entity.User;
import com.sky.dto.CustomerPageQueryDTO;
import com.sky.vo.CustomerVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.List;

@Mapper
public interface UserMapper {

    /**
     * 根据openid查询用户
     * @param openid
     * @return
     */
    @Select("select * from user where openid = #{openid}")
    User getByOpenid(String openid);

    @Select("select * from user where phone = #{phone} limit 1")
    User getByPhone(@Param("phone") String phone);

    @Select("select * from user where username = #{username} limit 1")
    User getByUsername(@Param("username") String username);

    List<User> getByName(@Param("name") String name);

    /**
     * 插入数据
     * @param user
     */
    void insert(User user);

    void insertPhoneCustomer(User user);

    List<CustomerVO> pageQuery(CustomerPageQueryDTO query);

    @Select("select * from user where id = #{id}")
    User getById(Long userId);

    /**
     * 根据动态条件统计用户数量
     * @param map
     * @return
     */
    Integer countByMap(Map map);
}
