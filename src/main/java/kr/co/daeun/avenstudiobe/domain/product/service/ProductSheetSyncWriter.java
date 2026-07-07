package kr.co.daeun.avenstudiobe.domain.product.service;

import kr.co.daeun.avenstudiobe.domain.brand.entity.Brand;
import kr.co.daeun.avenstudiobe.domain.product.entity.Material;
import kr.co.daeun.avenstudiobe.domain.product.entity.Product;
import kr.co.daeun.avenstudiobe.domain.product.entity.Size;
import kr.co.daeun.avenstudiobe.domain.product.entity.SizeMeasurement;
import kr.co.daeun.avenstudiobe.domain.product.repository.MaterialRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.ProductRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeMeasurementRepository;
import kr.co.daeun.avenstudiobe.domain.product.repository.SizeRepository;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetMaterialRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetProductRow;
import kr.co.daeun.avenstudiobe.domain.sheet.dto.SheetSizeMeasurementRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductSheetSyncWriter {

    private final ProductRepository productRepository;
    private final MaterialRepository materialRepository;
    private final SizeRepository sizeRepository;
    private final SizeMeasurementRepository sizeMeasurementRepository;

    @Transactional
    public void applyProduct(
            Brand brand,
            SheetProductRow productRow,
            List<SheetMaterialRow> materialRows,
            List<SheetSizeMeasurementRow> measurementRows
    ) {
        Product product = upsertProduct(brand, productRow);
        replaceMaterials(product, materialRows);
        replaceSizesAndMeasurements(product, measurementRows);
    }

    private Product upsertProduct(Brand brand, SheetProductRow row) {
        Product product = productRepository.findProductByBrandAndCode(brand, row.code());

        if (product == null) {
            return productRepository.save(Product.create(
                    brand,
                    row.code(),
                    row.name(),
                    row.category(),
                    row.gender(),
                    row.color(),
                    row.price(),
                    row.fitDescription(),
                    row.description(),
                    row.fabricationDescription()
            ));
        }

        product.update(
                row.name(),
                row.category(),
                row.gender(),
                row.color(),
                row.price(),
                row.fitDescription(),
                row.description(),
                row.fabricationDescription()
        );
        return product;
    }

    private void replaceMaterials(Product product, List<SheetMaterialRow> materialRows) {
        materialRepository.deleteByProduct(product);

        if (materialRows.isEmpty()) {
            return;
        }

        List<Material> materials = materialRows.stream()
                .map(row -> Material.of(product, row.name(), row.percent()))
                .toList();
        materialRepository.saveAll(materials);
    }

    private void replaceSizesAndMeasurements(Product product, List<SheetSizeMeasurementRow> measurementRows) {
        List<Size> existingSizes = sizeRepository.findByProduct(product);
        if (!existingSizes.isEmpty()) {
            sizeMeasurementRepository.deleteBySizeIn(existingSizes);
            sizeRepository.deleteByProduct(product);
        }

        if (measurementRows.isEmpty()) {
            return;
        }

        Map<String, List<SheetSizeMeasurementRow>> rowsBySizeLabel = measurementRows.stream()
                .collect(Collectors.groupingBy(SheetSizeMeasurementRow::sizeLabel));

        rowsBySizeLabel.forEach((sizeLabel, rowsForSize) -> {
            Size size = sizeRepository.save(Size.of(product, sizeLabel));
            List<SizeMeasurement> sizeMeasurements = rowsForSize.stream()
                    .map(row -> SizeMeasurement.of(size, row.part(), row.measurementName(), row.value(), row.unit()))
                    .toList();
            sizeMeasurementRepository.saveAll(sizeMeasurements);
        });
    }
}
