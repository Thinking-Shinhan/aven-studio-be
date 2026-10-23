package kr.co.daeun.avenstudiobe.domain.brand.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.dto.BrandHomeRespDto;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.repository.SheetConnectionRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import kr.co.daeun.avenstudiobe.global.exception.BrandNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BrandService {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final BrandRepository brandRepository;
    private final BrandMoodRepository brandMoodRepository;
    private final SheetConnectionRepository sheetConnectionRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public BrandHomeRespDto getHomePage(User user) {
        Long brandId = user.getBrand().getId();
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(BrandNotFoundException::new);

        Optional<BrandMood> brandMood = brandMoodRepository
                .findTopByBrandIdAndActiveTrueOrderByVersionDesc(brandId);

        long productCount = productRepository.countByBrand_Id(brandId);
        Optional<SheetConnection> sheetConnection = sheetConnectionRepository.findByBrandId(brandId);
        LocalDateTime sheetSyncedAt = sheetConnection
                .map(SheetConnection::getLastSyncedAt)
                .orElse(null);
        String sheetUrl = sheetConnection
                .map(SheetConnection::getUrl)
                .orElse(null);

        return new BrandHomeRespDto(
                brand.getName(),
                brandMood.isPresent(),
                brandMood.map(BrandMood::getBrandSummary).orElse(null),
                brandMood.map(BrandMood::getDesignPhilosophy).orElse(null),
                brandMood.map(mood -> parseToneKeywords(mood.getToneKeywords())).orElseGet(List::of),
                productCount,
                sheetSyncedAt != null,
                sheetUrl,
                sheetSyncedAt
        );
    }

    private List<String> parseToneKeywords(String toneKeywordsJson) {
        try {
            List<String> toneKeywords = objectMapper.readValue(toneKeywordsJson, STRING_LIST_TYPE);
            return toneKeywords == null ? List.of() : List.copyOf(toneKeywords);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("브랜드 무드 toneKeywords 파싱에 실패했습니다.", e);
        }
    }
}
