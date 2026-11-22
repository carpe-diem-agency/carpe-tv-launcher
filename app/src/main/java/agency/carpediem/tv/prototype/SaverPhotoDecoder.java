package agency.carpediem.tv.prototype;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.os.Build;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Decodes a sampled cached photo without displaying a decoder's partial result. */
final class SaverPhotoDecoder {
    private static final int MAX_WIDTH = 2560;
    private static final int MAX_HEIGHT = 1600;

    private SaverPhotoDecoder() {}

    /** Returns null for invalid data. Read failures propagate so the cache can retry later. */
    static Bitmap decode(File file, int minimumSampleSize) throws IOException {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return ImageDecoder.decodeBitmap(ImageDecoder.createSource(file), (decoder, info, source) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setMemorySizePolicy(ImageDecoder.MEMORY_POLICY_LOW_RAM);
                    decoder.setOnPartialImageListener(error -> false);
                    decoder.setTargetSampleSize(sampleSize(
                            info.getSize().getWidth(), info.getSize().getHeight(), minimumSampleSize));
                });
            } catch (ImageDecoder.DecodeException error) {
                if (error.getError() == ImageDecoder.DecodeException.SOURCE_EXCEPTION) throw error;
                return null;
            }
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        if (!hasCompleteContainer(file, options.outMimeType) || options.outWidth <= 0 || options.outHeight <= 0) {
            return null;
        }
        options.inJustDecodeBounds = false;
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        options.inSampleSize = sampleSize(options.outWidth, options.outHeight, minimumSampleSize);
        return BitmapFactory.decodeFile(file.getAbsolutePath(), options);
    }

    private static int sampleSize(int width, int height, int minimum) {
        int sample = minimum;
        while (width > (long) MAX_WIDTH * sample || height > (long) MAX_HEIGHT * sample) sample *= 2;
        return sample;
    }

    // Android 8's BitmapFactory can accept truncated data. Check the container boundary
    // for common cached formats before decoding. Full decoder validation starts at API 28.
    private static boolean hasCompleteContainer(File file, String mimeType) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if ("image/jpeg".equals(mimeType)) {
                if (length < 4) return false;
                input.seek(length - 2);
                return input.readUnsignedShort() == 0xffd9;
            }
            if ("image/png".equals(mimeType)) {
                if (length < 57) return false;
                input.seek(length - 12);
                return input.readInt() == 0 && input.readLong() == 0x49454e44ae426082L;
            }
            if ("image/webp".equals(mimeType)) {
                if (length < 12 || input.readInt() != 0x52494646) return false;
                long riffLength = Integer.toUnsignedLong(Integer.reverseBytes(input.readInt()));
                return riffLength + 8 == length && input.readInt() == 0x57454250;
            }
            return true;
        }
    }
}
