package kr.co.daeun.avenstudiobe.domain.sheet.dto;

public record SheetProductRow(
        String code,
        String name,
        String category,
        String gender,
        int price,
        String fitDescription,
        String description,
        String fabricationDescription,
        String color,
        int lining,
        int elasticity,
        int transparency,
        int touchFeeling,
        int thickness
) {
}
