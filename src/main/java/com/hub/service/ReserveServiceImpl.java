package com.hub.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hub.domain.Reserve;
import com.hub.domain.User;
import com.hub.dto.PageRequestDTO;
import com.hub.dto.PageResponseDTO;
import com.hub.dto.ReserveDTO;
import com.hub.repository.ReserveRepository;
import com.hub.repository.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@Transactional
@Log4j2
@RequiredArgsConstructor // 생성자 자동 주입
public class ReserveServiceImpl implements ReserveService {

	// 자동주입 대상은 final로
	private final ModelMapper modelMapper;
	private final ReserveRepository reserveRepository;
	private final UserRepository userRepository;

	// 데이터 삽입 메서드
	@Override
	public Long register(ReserveDTO reserveDTO) {

		// 유저 ID로 유저 정보 조회
		String urId = reserveDTO.getUrId();

		// 유저가 존재하는지 확인 후, 존재하지 않으면 예외 처리
		User user = userRepository.findByUrId(urId);
		if (user == null) {
			throw new RuntimeException("User not found with ID: " + urId);
		}

		// ReserveDTO를 Reserve 엔티티로 변환
		Reserve reserve = modelMapper.map(reserveDTO, Reserve.class);

		// 유저 설정
		reserve.setUser(user);
		
		// rsTotalPersonCnt 계산
	    reserve.setRsTotalPersonCnt(
	        reserve.getRsAdultPersonCnt() + 
	        reserve.getRsChildPersonCnt() + 
	        reserve.getRsPreagePersonCnt()
	    );

		log.info("@@@@@@@@@urId : " + reserve.getUser());

		// 예약 정보 저장
		Reserve savedReserve = reserveRepository.save(reserve);

		return savedReserve.getRsNb();
	}

	// 데이터 조회 메서드
	@Override
	public ReserveDTO get(Long rs_nb) {
		Optional<Reserve> result = reserveRepository.findById(rs_nb);

		Reserve reserve = result.orElseThrow();
		ReserveDTO dto = modelMapper.map(reserve, ReserveDTO.class);

		return dto;
	}

	// 데이터 수정 메서드
	@Override
	public void modify(ReserveDTO reserveDTO) {
		Optional<Reserve> result = reserveRepository.findById(reserveDTO.getRsNb());

		Reserve reserve = result.orElseThrow();

		reserve.changeRs_dt(reserveDTO.getRsDt());

		reserve.changeRs_adult_person_cnt(reserveDTO.getRsAdultPersonCnt());
		reserve.changeRs_child_person_cnt(reserveDTO.getRsChildPersonCnt());
		reserve.changeRs_preage_person_cnt(reserveDTO.getRsPreagePersonCnt());

		reserve.changeRs_visit_adult_cnt(reserveDTO.getRsVisitAdultCnt());
		reserve.changeRs_visit_child_cnt(reserveDTO.getRsVisitChildCnt());
		reserve.changeRs_visit_preage_cnt(reserveDTO.getRsVisitPreageCnt());

		reserve.changeRs_payment_complete_yn(reserveDTO.isRsPaymentCompleteYn());
		reserve.changeRs_visit_yn(reserveDTO.isRsVisitYn());

		reserve.changeRs_nm(reserveDTO.getRsNm());
		reserve.changeRs_phn(reserveDTO.getRsPhn());
		reserve.changeRs_significant(reserveDTO.getRsSignificant());

		reserveRepository.save(reserve);

	}

	// 데이터 삭제 메서드
	@Override
	public void remove(Long rs_nb) {
		reserveRepository.deleteById(rs_nb);

	}

	// 데이터 리스트 조회 메서드
	@Override
	public PageResponseDTO<ReserveDTO> list(PageRequestDTO pageRequestDTO) {

		String loginUrId = SecurityContextHolder.getContext().getAuthentication().getName(); // 로그인한 사용자 아이디

		// User 객체를 찾는 로직
		User user = userRepository.findByUrId(loginUrId); // 사용자 이름으로 User 객체 조회
		if (user == null) {
            throw new RuntimeException("User not found with ID: " + loginUrId);
        }
		
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		
		 boolean isAdmin = authentication.getAuthorities().stream()
                 .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals("ROLE_ADMIN")); // 관리자 권한 여부

		Pageable pageable = PageRequest.of(pageRequestDTO.getPage() - 1, // 1페이지가 0이므로 주의
				pageRequestDTO.getSize(), Sort.by("rsNb").descending());

		Page<Reserve> result;
		
		if (isAdmin) {
	        // 관리자일 경우 모든 데이터 조회
	        result = reserveRepository.findAll(pageable);
	    } else {
	        // 일반 사용자일 경우 자신의 urId와 일치하는 데이터만 조회
	        result = reserveRepository.findByUser(user, pageable);
	    }

		List<ReserveDTO> dtoList = result.getContent().stream()
				.map(reserve -> modelMapper.map(reserve, ReserveDTO.class)).collect(Collectors.toList());

		long totalCount = result.getTotalElements();

		PageResponseDTO<ReserveDTO> responseDTO = PageResponseDTO.<ReserveDTO>withAll().dtoList(dtoList)
				.pageRequestDTO(pageRequestDTO).totalCount(totalCount).build();

		return responseDTO;
	}
	
	// 예약일이 오늘까지인 예약 리스트 조회
    @Override
    public PageResponseDTO<ReserveDTO> activeReservationsList(PageRequestDTO pageRequestDTO) {
        String loginUrId = SecurityContextHolder.getContext().getAuthentication().getName();

        Pageable pageable = PageRequest.of(pageRequestDTO.getPage() - 1, pageRequestDTO.getSize(), Sort.by("rsNb").descending());
        LocalDateTime today = LocalDateTime.now(); // 오늘 날짜

        Page<Reserve> result = reserveRepository.findActiveReservationsByUrId(loginUrId, today, pageable);

        List<ReserveDTO> dtoList = result.getContent().stream()
            .map(reserve -> modelMapper.map(reserve, ReserveDTO.class))
            .collect(Collectors.toList());

        long totalCount = result.getTotalElements();
        return PageResponseDTO.<ReserveDTO>withAll()
            .dtoList(dtoList)
            .pageRequestDTO(pageRequestDTO)
            .totalCount(totalCount)
            .build();
    }

 // 결제 완료된 예약 리스트 조회
    @Override
    public PageResponseDTO<ReserveDTO> paidReservationsList(PageRequestDTO pageRequestDTO) {
        String loginUrId = SecurityContextHolder.getContext().getAuthentication().getName();

        Pageable pageable = PageRequest.of(pageRequestDTO.getPage() - 1, pageRequestDTO.getSize(), Sort.by("rsNb").descending());
        Page<Reserve> result = reserveRepository.findPaidReservationsByUrId(loginUrId, pageable);

        List<ReserveDTO> dtoList = result.getContent().stream()
            .map(reserve -> modelMapper.map(reserve, ReserveDTO.class))
            .collect(Collectors.toList());

        long totalCount = result.getTotalElements();
        return PageResponseDTO.<ReserveDTO>withAll()
            .dtoList(dtoList)
            .pageRequestDTO(pageRequestDTO)
            .totalCount(totalCount)
            .build();
    }

}
