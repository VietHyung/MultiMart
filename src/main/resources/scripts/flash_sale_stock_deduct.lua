-- KEYS[1]: flashsale:stock:{eventId}:{productId}
-- KEYS[2]: flashsale:buyers:{eventId}:{productId}
-- ARGV[1]: userId (String)
-- ARGV[2]: buyQuantity (Number)
-- ARGV[3]: purchaseLimitPerUser (Number)

-- 1. Kiểm tra tồn kho có tồn tại trên Redis không (nếu chưa warm-up)
local stock = redis.call('GET', KEYS[1])
if not stock then
    return -3 -- Lỗi: Chưa warm-up hoặc key không tồn tại
end

stock = tonumber(stock)
local buyQty = tonumber(ARGV[2])
local limit = tonumber(ARGV[3])
local userId = ARGV[1]

-- 2. Kiểm tra giới hạn mua của người dùng
local userBought = redis.call('HGET', KEYS[2], userId)
local currentBought = 0
if userBought then
    currentBought = tonumber(userBought)
end

if (currentBought + buyQty) > limit then
    return -2 -- Lỗi: Vượt quá giới hạn mua cho phép của người dùng
end

-- 3. Kiểm tra số lượng tồn kho còn đủ không
if stock < buyQty then
    return -1 -- Lỗi: Hết hàng
end

-- 4. Trừ kho và ghi nhận lượt mua một cách nguyên tử
redis.call('DECRBY', KEYS[1], buyQty)
redis.call('HINCRBY', KEYS[2], userId, buyQty)

-- Trả về số lượng tồn kho còn lại sau khi trừ thành công
return stock - buyQty
