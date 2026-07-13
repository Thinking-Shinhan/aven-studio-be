package kr.co.daeun.avenstudiobe.domain.detailpage.dto;

public record ProductCopyContext(
        String name,
        String category,
        String gender,
        String color,
        String fitDescription,
        String fabricationDescription,
        String elasticity,
        String touchFeeling,
        String thickness,
        String brandTone
) {
}
