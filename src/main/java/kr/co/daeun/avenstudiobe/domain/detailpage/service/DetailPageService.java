package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.*;
import kr.co.daeun.avenstudiobe.domain.detailpage.entity.DetailPage;
import kr.co.daeun.avenstudiobe.domain.detailpage.repository.DetailPageRepository;
import kr.co.daeun.avenstudiobe.domain.library.dto.DetailPageRespDto;
import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import kr.co.daeun.avenstudiobe.domain.product.entity.SizeMeasurement;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import kr.co.daeun.avenstudiobe.global.storage.GcsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DetailPageService {
    private static final String EXPORTED_STATUS = "EXPORTED";

    private final ProductService productService;
    private final MaterialRepository materialRepository;
    private final SizeRepository sizeRepository;
    private final SizeMeasurementRepository sizeMeasurementRepository;
    private final AssetService assetService;
    private final GcsService gcsService;
    private final DetailPageRepository detailPageRepository;

    @Transactional(readOnly = true)
    public DetailPageInitRespDto getInitData(Long productId, Authentication authentication) {
        Product product = productService.getAccessibleProduct(productId, authentication);

        List<Material> materials = materialRepository.findByProductId(productId);

        List<Size> sizes = sizeRepository.findByProductIdOrderByIdAsc(productId);

        List<Long> sizeIds = sizes.stream()
                .map(Size::getId)
                .toList();

        List<SizeMeasurement> sizeMeasurements = sizeIds.isEmpty()
                ? List.of()
                : sizeMeasurementRepository.findBySizeIdInOrderByIdAsc(sizeIds);

        Map<Long, List<SizeMeasurement>> measurementMap = sizeMeasurements.stream()
                .collect(Collectors.groupingBy(
                        measurement -> measurement.getSize().getId()
                ));

        List<MaterialInitRespDto> materialDtos = materials.stream()
                .map(material -> new MaterialInitRespDto(
                        material.getId(),
                        material.getName(),
                        material.getPercent()
                ))
                .toList();

        List<SizeInitRespDto> sizeDtos = sizes.stream()
                .map(size -> {
                    List<MeasurementInitRespDto> measurementDtos =
                            measurementMap.getOrDefault(size.getId(), List.of())
                                    .stream()
                                    .map(measurement -> new MeasurementInitRespDto(
                                            measurement.getId(),
                                            measurement.getPart(),
                                            measurement.getName(),
                                            measurement.getValue(),
                                            measurement.getUnit()
                                    ))
                                    .toList();

                    return new SizeInitRespDto(
                            size.getId(),
                            size.getSize(),
                            measurementDtos
                    );
                })
                .toList();

        ProductInitRespDto productDto = new ProductInitRespDto(
                product.getId(),
                product.getCode(),
                product.getName(),
                product.getCategory(),
                product.getGender(),
                product.getColor(),
                product.getPrice(),
                product.getFitDescription(),
                product.getDescription(),
                product.getFabricationDescription(),
                product.getLining(),
                product.getElasticity(),
                product.getTransparency(),
                product.getTouchFeeling(),
                product.getThickness(),
                materialDtos,
                sizeDtos
        );

        List<AssetInitRespDto> assetDtos = assetService.getInitAssets(productId);

        return new DetailPageInitRespDto(
                productDto,
                assetDtos
        );
    }

    @Transactional
    public DetailPageSaveRespDto saveDetailPageImage(Long productId, MultipartFile file, Authentication authentication) {
        Product product = productService.getAccessibleProductForUpdate(productId, authentication);

        GcsService.UploadedFile uploaded = gcsService.upload(file, productId);
        AtomicReference<String> previousObjectName = new AtomicReference<>();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    String previous = previousObjectName.get();
                    if (previous != null && !previous.isBlank()) {
                        gcsService.deleteQuietly(previous);
                    }
                    return;
                }

                gcsService.deleteQuietly(uploaded.objectName());
            }
        });

        DetailPage detailPage = detailPageRepository.findByProductId(productId)
                .map(existing -> {
                    previousObjectName.set(existing.getObjectName());
                    existing.update(EXPORTED_STATUS, uploaded.fileUrl(), uploaded.objectName(), uploaded.originalFilename(), uploaded.size());
                    return existing;
                })
                .orElseGet(() -> DetailPage.create(
                        product,
                        EXPORTED_STATUS,
                        uploaded.fileUrl(),
                        uploaded.objectName(),
                        uploaded.originalFilename(),
                        uploaded.size()
                ));

        DetailPage saved = detailPageRepository.save(detailPage);

        return new DetailPageSaveRespDto(
                saved.getId(),
                productId,
                saved.getStatus(),
                gcsService.generateSignedUrl(uploaded.objectName()),
                getStoredFilename(uploaded.objectName())
        );
    }

    private String getStoredFilename(String objectName) {
        int lastSeparator = objectName.lastIndexOf('/');
        return objectName.substring(lastSeparator + 1);
    }
}
