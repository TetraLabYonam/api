package com.example.attempt.controller.ticket;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class TicketService {
    private final StringRedisTemplate redis;

    private final DefaultRedisScript<List> ISSUE_SCRIPT = new DefaultRedisScript<>(
            """
            local issued = redis.call('get', KEYS[3])
            if issued then return {issued, 1} end
      
            local added = redis.call('sadd', KEYS[2], ARGV[1])
            if added == 0 then
              local n2 = redis.call('get', KEYS[3])
              return {n2, 1}
            end
      
            local newn = redis.call('incr', KEYS[1])
            redis.call('set', KEYS[3], newn)
            return {newn, 0}
            """, List.class);

    public void initRoom(String roomId) {
        redis.opsForValue().set(counterKey(roomId), "0");
        redis.delete(membersKey(roomId));
    }

    @SuppressWarnings("unchecked")
    public IssueResult issue(String roomId, String userKey) {
        var res = (List<Object>) redis.execute(
                ISSUE_SCRIPT,
                List.of(counterKey(roomId), membersKey(roomId), issuedKey(roomId, userKey)),
                userKey
        );
        long number = Long.parseLong(String.valueOf(res.get(0)));
        boolean duplicated = Integer.parseInt(String.valueOf(res.get(1))) == 1;
        long count = redis.opsForSet().size(membersKey(roomId));
        long last = Long.parseLong(redis.opsForValue().get(counterKey(roomId)));
        return new IssueResult(number, duplicated, count, last);
    }

    public Map<String,Object> snapshot(String roomId) {
        long last = Long.parseLong(redis.opsForValue().get(counterKey(roomId)));
        long count = redis.opsForSet().size(membersKey(roomId));
        return Map.of("lastNumber", last, "count", count);
    }

    public record IssueResult(long number, boolean duplicated, long count, long lastNumber) {}

    private String counterKey(String roomId){ return "rooms:%s:counter".formatted(roomId); }
    private String membersKey(String roomId){ return "rooms:%s:members".formatted(roomId); }
    private String issuedKey(String roomId, String userKey){ return "rooms:%s:issued:%s".formatted(roomId, userKey); }
}
