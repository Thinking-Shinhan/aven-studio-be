package kr.co.daeun.avenstudiobe.domain.asset.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetTest {

    @Test
    void initializesLikeToFalse() {
        Asset asset = Asset.builder().build();

        assertFalse(asset.getIsLiked());
    }

    @Test
    void togglesLikeSafely() {
        Asset asset = Asset.builder().isLiked(null).build();

        asset.toggleIsLiked();
        assertTrue(asset.getIsLiked());

        asset.toggleIsLiked();
        assertFalse(asset.getIsLiked());
    }
}
