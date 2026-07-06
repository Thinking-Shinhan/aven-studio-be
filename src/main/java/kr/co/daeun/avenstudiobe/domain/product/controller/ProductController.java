package kr.co.daeun.avenstudiobe.domain.product.controller;

import kr.co.daeun.avenstudiobe.domain.product.dto.ProductListRespDto;
import kr.co.daeun.avenstudiobe.domain.product.dto.ProductRespDto;
import kr.co.daeun.avenstudiobe.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<ProductListRespDto> getProductList(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String color,
            @RequestParam(required = false) String gender,
            @RequestParam(required = false) String keyword){

        ProductListRespDto products = productService.getProductList(category, color, gender, keyword);

        return ResponseEntity.ok().body(products);
    }

}
