-- seckill.lua
-- KEYS[1]: 库存的 key (e.g., seckill:stock:1)
-- KEYS[2]: 已购买用户集合的 key (e.g., seckill:users:1)
-- ARGV[1]: 当前请求的用户 ID

-- 1. 判断用户是否重复购买
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
    return 2 -- 2 代表重复购买
end

-- 2. 获取库存
local stock = tonumber(redis.call('get', KEYS[1]))
if stock <= 0 then
    return 1 -- 1 代表库存不足
end

-- 3. 扣减库存
redis.call('decr', KEYS[1])

-- 4. 记录购买用户
redis.call('sadd', KEYS[2], ARGV[1])

return 0 -- 0 代表秒杀成功