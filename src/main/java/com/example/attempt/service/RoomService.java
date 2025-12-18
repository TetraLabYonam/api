package com.example.attempt.service;

import com.example.attempt.domain.Room;
import com.example.attempt.dto.QueueRoomDto;
import com.example.attempt.dto.RoomCreateRequest;
import com.example.attempt.dto.RoomUpdateRequest;
import com.example.attempt.exception.ResourceNotFoundException;
import com.example.attempt.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Room 비즈니스 로직 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    /**
     * 모든 활성화된 방 조회
     */
    public List<QueueRoomDto> getAllActiveRooms() {
        log.info("모든 활성화된 방 조회");
        return roomRepository.findByIsActiveTrueOrderByCreatedAtDesc()
                .stream()
                .map(QueueRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 모든 방 조회 (비활성화 포함)
     */
    public List<QueueRoomDto> getAllRooms() {
        log.info("모든 방 조회");
        return roomRepository.findAll()
                .stream()
                .map(QueueRoomDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 방 ID로 조회
     */
    public QueueRoomDto getRoomById(Long id) {
        log.info("방 조회 - ID: {}", id);
        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id));
        return QueueRoomDto.fromEntity(room);
    }

    /**
     * 방 UID로 조회
     */
    public QueueRoomDto getRoomByUid(String roomUid) {
        log.info("방 조회 - UID: {}", roomUid);
        Room room = roomRepository.findByRoomUid(roomUid)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. UID: " + roomUid));
        return QueueRoomDto.fromEntity(room);
    }

    /**
     * 새 방 생성
     */
    @Transactional
    public QueueRoomDto createRoom(RoomCreateRequest request) {
        log.info("새 방 생성 요청 - 이름: {}", request.getRoomName());

        Room room = new Room();
        room.setTitle(request.getRoomName());

        // UID 설정 (제공되지 않으면 자동 생성)
        if (request.getRoomUid() != null && !request.getRoomUid().trim().isEmpty()) {
            // 중복 체크
            if (roomRepository.findByRoomUid(request.getRoomUid()).isPresent()) {
                throw new IllegalArgumentException("이미 존재하는 방 UID입니다: " + request.getRoomUid());
            }
            room.setRoomUid(request.getRoomUid());
        } else {
            room.setRoomUid(generateRoomUid());
        }

        room.setIsActive(true);
        room.setCurrentNumber(0);
        room.setLastIssuedNumber(0);
        room.setCreatedAt(LocalDateTime.now());
        room.setUpdatedAt(LocalDateTime.now());

        Room savedRoom = roomRepository.save(room);
        log.info("방 생성 완료 - ID: {}, UID: {}, 이름: {}",
                savedRoom.getId(), savedRoom.getRoomUid(), savedRoom.getTitle());

        return QueueRoomDto.fromEntity(savedRoom);
    }

    /**
     * 방 정보 수정
     */
    @Transactional
    public QueueRoomDto updateRoom(Long id, RoomUpdateRequest request) {
        log.info("방 수정 요청 - ID: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id));

        // 수정 가능한 필드만 업데이트
        if (request.getRoomName() != null && !request.getRoomName().trim().isEmpty()) {
            room.setTitle(request.getRoomName());
        }

        if (request.getIsActive() != null) {
            room.setIsActive(request.getIsActive());
        }

        if (request.getCurrentNumber() != null) {
            room.setCurrentNumber(request.getCurrentNumber());
        }

        Room updatedRoom = roomRepository.save(room);
        log.info("방 수정 완료 - ID: {}, 이름: {}", updatedRoom.getId(), updatedRoom.getTitle());

        return QueueRoomDto.fromEntity(updatedRoom);
    }

    /**
     * 방 비활성화
     */
    @Transactional
    public void deactivateRoom(Long id) {
        log.info("방 비활성화 요청 - ID: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id));

        room.setIsActive(false);
        roomRepository.save(room);

        log.info("방 비활성화 완료 - ID: {}", id);
    }

    /**
     * 방 활성화
     */
    @Transactional
    public void activateRoom(Long id) {
        log.info("방 활성화 요청 - ID: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id));

        room.setIsActive(true);
        roomRepository.save(room);

        log.info("방 활성화 완료 - ID: {}", id);
    }

    /**
     * 방 삭제 (물리적 삭제)
     */
    @Transactional
    public void deleteRoom(Long id) {
        log.info("방 삭제 요청 - ID: {}", id);

        if (!roomRepository.existsById(id)) {
            throw new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id);
        }

        roomRepository.deleteById(id);
        log.info("방 삭제 완료 - ID: {}", id);
    }

    /**
     * 방 번호 초기화
     */
    @Transactional
    public QueueRoomDto resetRoomNumbers(Long id) {
        log.info("방 번호 초기화 요청 - ID: {}", id);

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("방을 찾을 수 없습니다. ID: " + id));

        room.setCurrentNumber(0);
        room.setLastIssuedNumber(0);
        Room updatedRoom = roomRepository.save(room);

        log.info("방 번호 초기화 완료 - ID: {}", id);
        return QueueRoomDto.fromEntity(updatedRoom);
    }

    /**
     * 고유한 방 UID 생성
     */
    private String generateRoomUid() {
        String uid;
        do {
            uid = UUID.randomUUID().toString().substring(0, 16);
        } while (roomRepository.findByRoomUid(uid).isPresent());
        return uid;
    }

    /**
     * 여러 방 일괄 생성
     */
    @Transactional
    public List<QueueRoomDto> createRooms(List<String> roomNames) {
        log.info("여러 방 일괄 생성 요청 - 개수: {}", roomNames.size());

        List<Room> rooms = roomNames.stream()
                .map(name -> {
                    Room room = new Room();
                    room.setTitle(name);
                    room.setRoomUid(generateRoomUid());
                    room.setIsActive(true);
                    room.setCurrentNumber(0);
                    room.setLastIssuedNumber(0);
                    room.setCreatedAt(LocalDateTime.now());
                    room.setUpdatedAt(LocalDateTime.now());
                    return room;
                })
                .collect(Collectors.toList());

        List<Room> savedRooms = roomRepository.saveAll(rooms);
        log.info("방 일괄 생성 완료 - 개수: {}", savedRooms.size());

        return savedRooms.stream()
                .map(QueueRoomDto::fromEntity)
                .collect(Collectors.toList());
    }
}