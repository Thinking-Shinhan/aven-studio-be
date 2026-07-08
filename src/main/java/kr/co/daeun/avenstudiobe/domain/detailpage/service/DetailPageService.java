package kr.co.daeun.avenstudiobe.domain.detailpage.service;

import kr.co.daeun.avenstudiobe.domain.asset.entity.Asset;
import kr.co.daeun.avenstudiobe.domain.asset.repository.AssetRepository;
import kr.co.daeun.avenstudiobe.domain.asset.service.AssetService;
import kr.co.daeun.avenstudiobe.domain.detailpage.dto.*;
import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import kr.co.daeun.avenstudiobe.domain.product.entity.SizeMeasurement;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.global.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class DetailPageService {
    private final ProductRepository productRepository;
    private final AssetRepository assetRepository;
    private final MaterialRepository materialRepository;
    private final SizeRepository sizeRepository;
    private final SizeMeasurementRepository sizeMeasurementRepository;
    private final AssetService assetService;

    @Transactional(readOnly = true)
    public DetailPageInitRespDto getInitData(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(ProductNotFoundException::new);

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
}
