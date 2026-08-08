package net.wsdjeg.nova;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.HashMap;
import java.util.Map;

/**
 * 二维码生成工具类
 * 使用 ZXing 库在本地将字符串生成为二维码 Bitmap
 */
public class QRCodeUtils {

    private static final String TAG = "QRCodeUtils";

    /**
     * 将字符串生成为二维码 Bitmap
     *
     * @param content   要编码的字符串（如微信登录 URL）
     * @param size      二维码尺寸（像素）
     * @return 生成的 Bitmap，失败返回 null
     */
    public static Bitmap generateQRCode(String content, int size) {
        if (content == null || content.trim().isEmpty()) {
            Log.e(TAG, "Content is null or empty");
            return null;
        }

        // 先尝试 M 级纠错，失败后降级到 L 级
        Bitmap bitmap = tryGenerate(content, size, ErrorCorrectionLevel.M);
        if (bitmap == null) {
            Log.w(TAG, "QR code generation failed with ECC level M, retrying with L");
            bitmap = tryGenerate(content, size, ErrorCorrectionLevel.L);
        }
        return bitmap;
    }

    /**
     * 尝试用指定纠错级别生成二维码
     */
    private static Bitmap tryGenerate(String content, int size, ErrorCorrectionLevel eccLevel) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.ERROR_CORRECTION, eccLevel);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);

            int width = bitMatrix.getWidth();
            int height = bitMatrix.getHeight();
            int[] pixels = new int[width * height];

            for (int y = 0; y < height; y++) {
                int offset = y * width;
                for (int x = 0; x < width; x++) {
                    pixels[offset + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
                }
            }

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
            return bitmap;
        } catch (WriterException e) {
            Log.e(TAG, "WriterException with ECC " + eccLevel + ": " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "IllegalArgumentException with ECC " + eccLevel + ": " + e.getMessage(), e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error with ECC " + eccLevel + ": " + e.getMessage(), e);
        }
        return null;
    }
}

