package kr.co.daeun.avenstudiobe.domain.user.service;

import kr.co.daeun.avenstudiobe.domain.user.dto.RegisterReqDto;
import kr.co.daeun.avenstudiobe.domain.user.dto.RegisterRespDto;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.user.repository.UsersRepository;
import kr.co.daeun.avenstudiobe.global.exception.DuplicateUserIdException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UsersService {

    private final UsersRepository usersRepository;
    private final BrandRepository brandRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public RegisterRespDto register(RegisterReqDto dto) {

        if (!dto.getPassword().equals(dto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        if (usersRepository.existsByLoginId(dto.getLoginId())) {
            throw new DuplicateUserIdException();
        }

        Brand brand = brandRepository.findByName(dto.getBrandName())
                .orElseGet(() -> brandRepository.save(
                        Brand.builder()
                                .name(dto.getBrandName())
                                .build()
                ));

        User user = User.builder()
                .loginId(dto.getLoginId())
                .name(dto.getName())
                .password(passwordEncoder.encode(dto.getPassword()))
                .brand(brand)
                .build();

        User saved = usersRepository.save(user);

        return new RegisterRespDto(
                saved.getId(),
                saved.getLoginId(),
                saved.getName(),
                saved.getBrand().getId(),
                saved.getBrand().getName()
        );
    }
}
