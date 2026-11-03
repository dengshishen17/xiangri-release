package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    public IUserService userService;


    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取登录用户
        Long userid = UserHolder.getUser().getId();
        String key="follows"+userid;
        //1.判断到底是关注还是取关
        if(isFollow) {
            //2.关注，新增数据
            Follow follow=new Follow();
            follow.setUserId(userid);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);

            if (isSuccess){
                //把关注用户的id放入到redis的set集合

                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            //3.取消关注,删除
            boolean isSuccess = remove(new QueryWrapper<Follow>()
                    .eq("user_id", userid).eq("follow_user_id", followUserId));
            if (isSuccess){
                //把关注的用户从set移除
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }

        }

        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取登录用户
        Long userid = UserHolder.getUser().getId();
        //1.查询是否关注
        Integer count = query().eq("user_id", userid).eq("follow_user_id", followUserId).count();
        //判断
        return Result.ok(count > 0);
    }

    @Override
    public Result followCommons(Long id) {
        //获取当前用户
        Long userId = UserHolder.getUser().getId();
        //当前登录用户关注的集合
        String key="follows"+userId;
        //求交集
        //目标用户关注的集合
        String key2="follows"+id;
        //任一方的关注集合不存在时，交集必然为空，直接返回空列表
        if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey(key))
                || !Boolean.TRUE.equals(stringRedisTemplate.hasKey(key2))) {
            return Result.ok(Collections.emptyList());
        }
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect==null){
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        //4.查询用户
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
