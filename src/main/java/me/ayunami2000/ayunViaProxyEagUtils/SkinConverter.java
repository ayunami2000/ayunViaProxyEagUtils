package me.ayunami2000.ayunViaProxyEagUtils;

public class SkinConverter {
    public static void convert64x32to64x64(final int[] skinIn, final int[] skinOut) {
        copyRawPixels(skinIn, skinOut, 0, 0, 0, 0, 64, 32, false);
        copyRawPixels(skinIn, skinOut, 24, 48, 20, 4, 16, 8, 20);
        copyRawPixels(skinIn, skinOut, 28, 48, 24, 8, 16, 12, 20);
        copyRawPixels(skinIn, skinOut, 20, 52, 16, 8, 20, 12, 32);
        copyRawPixels(skinIn, skinOut, 24, 52, 20, 4, 20, 8, 32);
        copyRawPixels(skinIn, skinOut, 28, 52, 24, 0, 20, 4, 32);
        copyRawPixels(skinIn, skinOut, 32, 52, 28, 12, 20, 16, 32);
        copyRawPixels(skinIn, skinOut, 40, 48, 36, 44, 16, 48, 20);
        copyRawPixels(skinIn, skinOut, 44, 48, 40, 48, 16, 52, 20);
        copyRawPixels(skinIn, skinOut, 36, 52, 32, 48, 20, 52, 32);
        copyRawPixels(skinIn, skinOut, 40, 52, 36, 44, 20, 48, 32);
        copyRawPixels(skinIn, skinOut, 44, 52, 40, 40, 20, 44, 32);
        copyRawPixels(skinIn, skinOut, 48, 52, 44, 52, 20, 56, 32);
    }

    private static void copyRawPixels(final int[] imageIn, final int[] imageOut, final int dx1, final int dy1, final int dx2, final int sx1, final int sy1, final int sx2, final int sy2) {
        if (dx1 > dx2) {
            copyRawPixels(imageIn, imageOut, sx1, sy1, dx2, dy1, sx2 - sx1, sy2 - sy1, true);
        } else {
            copyRawPixels(imageIn, imageOut, sx1, sy1, dx1, dy1, sx2 - sx1, sy2 - sy1, false);
        }
    }

    private static void copyRawPixels(final int[] imageIn, final int[] imageOut, final int srcX, final int srcY, final int dstX, final int dstY, final int width, final int height, final boolean flip) {
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                final int i = imageIn[(srcY + y) * 64 + srcX + x];
                int j;
                if (flip) {
                    j = (dstY + y) * 64 + dstX + width - x - 1;
                } else {
                    j = (dstY + y) * 64 + dstX + x;
                }
                imageOut[j] = i;
            }
        }
    }
}
