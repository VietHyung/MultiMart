-- KEYS[1]: flashsale:stock:{eventId}:{productId}
-- KEYS[2]: flashsale:buyers:{eventId}:{productId}
-- ARGV[1]: userId (String)
-- ARGV[2]: rollbackQuantity (Number)

local stockKey = KEYS[1]
local buyersKey = KEYS[2]
local userId = ARGV[1]
local rollbackQty = tonumber(ARGV[2])

-- 1. Hoàn trả số lượng tồn kho trên Redis nếu key tồn tại
local updatedStock = -1
if redis.call('EXISTS', stockKey) == 1 then
    updatedStock = redis.call('INCRBY', stockKey, rollbackQty)
end

-- 2. Hoàn trả hạn mức đã mua trong Hash người mua
if redis.call('EXISTS', buyersKey) == 1 then
    local userBought = redis.call('HGET', buyersKey, userId)
    if userBought then
        local currentBought = tonumber(userBought)
        local remainingBought = currentBought - rollbackQty
        if remainingBought <= 0 then
            redis.call('HDEL', buyersKey, userId)
        else
            redis.call('HSET', buyersKey, userId, tostring(remainingBought))
        end
    end
end

-- Trả về số lượng tồn kho mới sau khi hoàn trả (hoặc -1 nếu key không còn tồn tại)
return updatedStock
