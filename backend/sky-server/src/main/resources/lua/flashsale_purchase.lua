local stockKey = KEYS[1]
local userCountKey = KEYS[2]
local activityInfoKey = KEYS[3]
local requestKey = KEYS[4]

local userId = ARGV[1]
local nowTime = tonumber(ARGV[2])
local failPrefix = ARGV[3]
local resultTtlSeconds = tonumber(ARGV[4])

-- Request idempotency and quota reservation must be one Redis atomic operation.
if redis.call('exists', requestKey) == 1 then
    return 2
end

local function fail(code, message)
    redis.call('set', requestKey, failPrefix .. message, 'EX', resultTtlSeconds)
    return code
end

if redis.call('exists', activityInfoKey) == 0 then
    return fail(-1, ARGV[5])
end

local status = tonumber(redis.call('hget', activityInfoKey, 'status'))
local startTime = tonumber(redis.call('hget', activityInfoKey, 'startTime'))
local endTime = tonumber(redis.call('hget', activityInfoKey, 'endTime'))
local limitPerUser = tonumber(redis.call('hget', activityInfoKey, 'limitPerUser'))

if status == nil or status ~= 1 then
    return fail(-1, ARGV[5])
end

if startTime ~= nil and nowTime < startTime then
    return fail(-4, ARGV[9])
end

if endTime ~= nil and nowTime > endTime then
    return fail(-5, ARGV[10])
end

local stock = tonumber(redis.call('get', stockKey))
if stock == nil then
    return fail(-1, ARGV[5])
end

if stock <= 0 then
    return fail(-2, ARGV[6])
end

local purchasedCount = tonumber(redis.call('hget', userCountKey, userId))
if purchasedCount ~= nil and purchasedCount >= limitPerUser then
    return fail(-3, ARGV[7])
end

redis.call('decrby', stockKey, 1)
redis.call('hincrby', userCountKey, userId, 1)
redis.call('set', requestKey, ARGV[8], 'EX', resultTtlSeconds)
return 1
