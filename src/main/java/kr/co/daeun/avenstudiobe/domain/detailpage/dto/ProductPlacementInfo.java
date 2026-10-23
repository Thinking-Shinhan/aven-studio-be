package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record ProductPlacementInfo(
        Long productId,
        String name,
        String category,
        String gender,
        String color,
        String description,
        String fitDescription,
        String fabricationDescription
) {
}
