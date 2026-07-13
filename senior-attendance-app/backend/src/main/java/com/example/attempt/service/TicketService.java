package com.example.attempt.service;

import com.example.attempt.domain.Room;
import com.example.attempt.domain.TicketIssuance;
import com.example.attempt.repository.RoomRepository;
import com.example.attempt.repository.TicketIssuanceRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ThreadLocalRandom;


@Service
@RequiredArgsConstructor
public class TicketService {

    private final RoomRepository roomRepository;
    private final TicketIssuanceRepository tiRepo;

    @Transactional
    public Map<String, Object> snapshot(String roomUid) {
        Room r = roomRepository.findByRoomUid(roomUid).orElseThrow(() -> notFound(roomUid));
        long count = tiRepo.countByRoomId(r.getId());
        return Map.of("lastNumber", r.getCurrentNumber(), "count", count);
    }

    @Transactional
    public Map<String,Object> issue(String roomUid, String userKey){
        // 1) 방 잠금
        Room r = roomRepository.findByRoomUidForUpdate(roomUid).orElseThrow(() -> notFound(roomUid));

        // 2) 이미 발급된 경우
        var prev = tiRepo.findByRoomIdAndUserKey(r.getId(), userKey);
        if (prev.isPresent()) {
            long count = tiRepo.countByRoomId(r.getId());
            return Map.of("number", prev.get().getNumber(), "duplicated", true,
                    "lastNumber", r.getCurrentNumber(), "count", count);
        }

        // 3) 새 번호 배정
        int next = r.getCurrentNumber() + 1;
        r.setCurrentNumber(next);

        try {
            tiRepo.save(new TicketIssuance(r, userKey, next));
            long count = tiRepo.countByRoomId(r.getId());
            return Map.of("number", next, "duplicated", false, "lastNumber", next, "count", count);
        } catch (DataIntegrityViolationException e) {
            // 드물게 동일 트랜잭션 외부 충돌 시: 기존 번호 재조회 후 반환
            var again = tiRepo.findByRoomIdAndUserKey(r.getId(), userKey)
                    .orElseThrow(() -> e);
            long count = tiRepo.countByRoomId(r.getId());
            return Map.of("number", again.getNumber(), "duplicated", true,
                    "lastNumber", r.getCurrentNumber(), "count", count);
        }
    }

    @Transactional
    public String createRoom(@Nullable String title){
        Room r = new Room();
        r.setRoomUid(randomUid(8));
        r.setTitle(title);
        r.setCurrentNumber(0);
        roomRepository.save(r);
        return r.getRoomUid();
    }

    @Transactional(readOnly = true)
    public java.util.List<Room> getAllRooms() {
        return roomRepository.findAll();
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> getAllRoomsWithDetails() {
        var rooms = roomRepository.findAll();
        return rooms.stream()
                .map(room -> {
                    long count = tiRepo.countByRoomId(room.getId());
                    return Map.<String, Object>of(
                            "roomUid", room.getRoomUid(),
                            "title", room.getTitle() != null ? room.getTitle() : "",
                            "currentNumber", room.getCurrentNumber(),
                            "issuedCount", count,
                            "createdAt", room.getCreatedAt().toString()
                    );
                })
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional
    public void resetRoom(String roomUid) {
        Room r = roomRepository.findByRoomUid(roomUid).orElseThrow(() -> notFound(roomUid));
        r.setCurrentNumber(0);
        roomRepository.save(r);
        // 해당 방의 모든 발급 기록 삭제
        tiRepo.deleteByRoomId(r.getId());
    }

    @Transactional(readOnly = true)
    public java.util.List<Map<String, Object>> getIssuances(String roomUid) {
        Room r = roomRepository.findByRoomUid(roomUid).orElseThrow(() -> notFound(roomUid));
        var issuances = tiRepo.findByRoomIdOrderByNumberAsc(r.getId());
        return issuances.stream()
                .map(ti -> Map.<String, Object>of(
                        "number", ti.getNumber(),
                        "userKey", ti.getUserKey(),
                        "issuedAt", ti.getIssuedAt().toString()
                ))
                .collect(java.util.stream.Collectors.toList());
    }

    @Transactional(readOnly = true)
    public String getUserKeyByNumber(String roomUid, Integer number) {
        Room r = roomRepository.findByRoomUid(roomUid).orElseThrow(() -> notFound(roomUid));
        var issuance = tiRepo.findByRoomIdAndNumber(r.getId(), number);
        return issuance.map(com.example.attempt.domain.TicketIssuance::getUserKey).orElse(null);
    }


    private RuntimeException notFound(String uid){
        return new NoSuchElementException("room not found: " + uid);
    }

    private static String randomUid(int n){
        String abc="123456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        ThreadLocalRandom tlr = ThreadLocalRandom.current();
        StringBuilder sb=new StringBuilder(n);
        for(int i=0;i<n;i++) sb.append(abc.charAt(tlr.nextInt(abc.length())));
        return sb.toString();
    }

}
