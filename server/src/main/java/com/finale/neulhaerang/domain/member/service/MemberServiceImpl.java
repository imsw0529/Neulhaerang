package com.finale.neulhaerang.domain.member.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import javax.transaction.Transactional;

import org.springframework.stereotype.Service;

import com.finale.neulhaerang.domain.member.dto.response.MemberCharacterResDto;
import com.finale.neulhaerang.domain.member.dto.response.MemberStatusResDto;
import com.finale.neulhaerang.domain.member.entity.CharacterInfo;
import com.finale.neulhaerang.domain.member.entity.Device;
import com.finale.neulhaerang.domain.member.entity.Member;
import com.finale.neulhaerang.domain.member.repository.CharacterInfoRepository;
import com.finale.neulhaerang.domain.member.repository.DeviceRepository;
import com.finale.neulhaerang.domain.member.repository.MemberRepository;
import com.finale.neulhaerang.global.exception.member.NonExistCharacterInfoException;
import com.finale.neulhaerang.global.exception.member.NonExistDeviceException;
import com.finale.neulhaerang.global.exception.member.NonExistMemberException;
import com.finale.neulhaerang.global.util.AuthenticationHandler;
import com.finale.neulhaerang.global.util.RedisUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService{
	private final MemberRepository memberRepository;
	private final DeviceRepository deviceRepository;
	private final CharacterInfoRepository characterInfoRepository;
	private final RedisUtil redisUtil;

	private final AuthenticationHandler authenticationHandler;

	// 멤버 상태 정보 조회 (나태도, 피로도) -> MongoDB에서 조회
	@Override
	public MemberStatusResDto findStatusByMemberId(long memberId) {
		log.info("MongoDB로부터 Member의 나태도, 피로도 조회.");
		return null;
	}

	// 멤버 캐릭터 조회
	@Override
	public MemberCharacterResDto findCharacterByMemberId(long memberId) throws NonExistCharacterInfoException {
		log.info("캐릭터 타입, 스킨, 의상 정보를 조회.");
		Optional<CharacterInfo> optionalCharacterInfo = characterInfoRepository.findCharacterInfoByMember_Id(memberId);
		if(optionalCharacterInfo.isEmpty()) {
			throw new NonExistCharacterInfoException();
		}
		return MemberCharacterResDto.from(optionalCharacterInfo.get());
	}

	@Override
	public Member loadMemberByDeviceToken(String deviceToken) throws NonExistDeviceException, NonExistMemberException {
		log.info("받아온 토큰으로 Auth 생성을 위한 멤버 조회");
		Optional<Device> optionalDevice = deviceRepository.findDeviceByDeviceToken(deviceToken);
		if(optionalDevice.isEmpty()) {
			throw new NonExistDeviceException();
		}
		Optional<Member> optionalMember = memberRepository.findMemberByIdAndWithdrawalDateIsNull(optionalDevice.get().getMember().getId());
		if(optionalMember.isEmpty()) {
			throw new NonExistMemberException();
		}
		return optionalMember.get();
	}

	@Transactional
	@Override
	public void removeMember() throws NonExistMemberException {
		long memberId = authenticationHandler.getLoginMemberId();
		Optional<Member> optionalMember = memberRepository.findMemberByIdAndWithdrawalDateIsNull(memberId);
		if(optionalMember.isEmpty()) {
			throw new NonExistMemberException();
		}

		Member member = optionalMember.get();
		member.updateWithdrawalDate(LocalDateTime.now());

		List<Device> devices = deviceRepository.findDevicesByMember_Id(member.getId());
		devices.stream().forEach((device) -> {
			redisUtil.deleteData(device.getDeviceToken());
			deviceRepository.delete(device);
		});
	}
}
