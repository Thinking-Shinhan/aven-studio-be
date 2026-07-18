package kr.co.daeun.avenstudiobe.domain.brand.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.daeun.avenstudiobe.domain.brand.dto.BrandHomeRespDto;
import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.brand.repository.BrandRepository;
import kr.co.daeun.avenstudiobe.domain.brandmood.entity.BrandMood;
import kr.co.daeun.avenstudiobe.domain.brandmood.repository.BrandMoodRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.sheet.entity.SheetConnection;
import kr.co.daeun.avenstudiobe.domain.sheet.repository.SheetConnectionRepository;
import kr.co.daeun.avenstudiobe.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandServiceTest {

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private BrandMoodRepository brandMoodRepository;

    @Mock
    private SheetConnectionRepository sheetConnectionRepository;

    @Mock
    private ProductRepository productRepository;

    private BrandService brandService;

    @BeforeEach
    void setUp() {
        brandService = new BrandService(
                brandRepository,
                brandMoodRepository,
                sheetConnectionRepository,
                productRepository,
                new ObjectMapper()
        );
    }

    @Test
    void returnsHomeDataForTheUsersBrand() {
        Brand brand = mock(Brand.class);
        User user = mock(User.class);
        BrandMood brandMood = mock(BrandMood.class);
        SheetConnection sheetConnection = mock(SheetConnection.class);
        LocalDateTime lastSyncedAt = LocalDateTime.of(2026, 7, 18, 20, 30);

        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(1L);
        when(brand.getName()).thenReturn("AVEN");
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(brandMood));
        when(brandMood.getBrandSummary()).thenReturn("프리미엄 아웃도어 브랜드");
        when(brandMood.getDesignPhilosophy()).thenReturn("Engineered Elegance");
        when(brandMood.getToneKeywords())
                .thenReturn("[\"정제됨\",\"차분함\",\"관찰적\",\"아카이브적\",\"미니멀\"]");
        when(productRepository.countByBrand_Id(1L)).thenReturn(152L);
        when(sheetConnectionRepository.findByBrandId(1L)).thenReturn(Optional.of(sheetConnection));
        when(sheetConnection.getLastSyncedAt()).thenReturn(lastSyncedAt);

        BrandHomeRespDto response = brandService.getHomePage(user);

        assertEquals("AVEN", response.getBrandName());
        assertTrue(response.isBrandMoodRegistered());
        assertEquals("프리미엄 아웃도어 브랜드", response.getBrandSummary());
        assertEquals("Engineered Elegance", response.getDesignPhilosophy());
        assertEquals(List.of("정제됨", "차분함", "관찰적", "아카이브적", "미니멀"), response.getToneKeywords());
        assertEquals(152L, response.getProductCount());
        assertTrue(response.isSheetSynced());
        assertEquals(lastSyncedAt, response.getSheetSyncedAt());
    }

    @Test
    void returnsNullSyncedAtWhenTheBrandHasNoSheetConnection() {
        Brand brand = mock(Brand.class);
        User user = mock(User.class);
        BrandMood brandMood = mock(BrandMood.class);

        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(1L);
        when(brand.getName()).thenReturn("AVEN");
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.of(brandMood));
        when(brandMood.getToneKeywords()).thenReturn("[]");
        when(sheetConnectionRepository.findByBrandId(1L)).thenReturn(Optional.empty());

        BrandHomeRespDto response = brandService.getHomePage(user);

        assertFalse(response.isSheetSynced());
        assertNull(response.getSheetSyncedAt());
    }

    @Test
    void returnsUnregisteredStateWhenTheBrandHasNoMood() {
        Brand brand = mock(Brand.class);
        User user = mock(User.class);
        LocalDateTime lastSyncedAt = LocalDateTime.of(2026, 7, 18, 20, 30);
        SheetConnection sheetConnection = mock(SheetConnection.class);

        when(user.getBrand()).thenReturn(brand);
        when(brand.getId()).thenReturn(1L);
        when(brand.getName()).thenReturn("AVEN");
        when(brandRepository.findById(1L)).thenReturn(Optional.of(brand));
        when(brandMoodRepository.findTopByBrandIdAndActiveTrueOrderByVersionDesc(1L))
                .thenReturn(Optional.empty());
        when(productRepository.countByBrand_Id(1L)).thenReturn(3L);
        when(sheetConnectionRepository.findByBrandId(1L)).thenReturn(Optional.of(sheetConnection));
        when(sheetConnection.getLastSyncedAt()).thenReturn(lastSyncedAt);

        BrandHomeRespDto response = brandService.getHomePage(user);

        assertEquals("AVEN", response.getBrandName());
        assertFalse(response.isBrandMoodRegistered());
        assertNull(response.getBrandSummary());
        assertNull(response.getDesignPhilosophy());
        assertEquals(List.of(), response.getToneKeywords());
        assertEquals(3L, response.getProductCount());
        assertTrue(response.isSheetSynced());
        assertEquals(lastSyncedAt, response.getSheetSyncedAt());
    }
}
