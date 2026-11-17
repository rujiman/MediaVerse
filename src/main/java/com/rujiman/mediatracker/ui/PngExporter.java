package com.rujiman.mediatracker.ui;

import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Guarda una imagen de JavaFX (por ejemplo un snapshot de la vitrina)
 * como PNG, sin depender de javafx-swing ni de ImageIO/AWT.
 *
 * El formato PNG es sencillo: firma + bloque IHDR (tamaño y tipo de
 * color) + bloque IDAT (los píxeles RGBA comprimidos con zlib, cada
 * fila precedida de un byte de filtro 0) + bloque IEND. Cada bloque
 * lleva su longitud, su tipo, sus datos y un CRC32.
 */
public final class PngExporter {

    private PngExporter() {}

    public static void save(WritableImage image, File file) throws IOException {
        int w = (int) image.getWidth();
        int h = (int) image.getHeight();
        PixelReader reader = image.getPixelReader();

        // Píxeles crudos: por fila, 1 byte de filtro (0 = ninguno) + RGBA
        ByteArrayOutputStream raw = new ByteArrayOutputStream(h * (w * 4 + 1));
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            reader.getPixels(0, y, w, 1, javafx.scene.image.PixelFormat.getIntArgbInstance(), row, 0, w);
            raw.write(0);
            for (int argb : row) {
                raw.write((argb >> 16) & 0xff); // R
                raw.write((argb >> 8) & 0xff);  // G
                raw.write(argb & 0xff);         // B
                raw.write((argb >> 24) & 0xff); // A
            }
        }

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream z = new DeflaterOutputStream(compressed, new Deflater(Deflater.BEST_SPEED))) {
            raw.writeTo(z);
        }

        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file))) {
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});

            ByteArrayOutputStream ihdr = new ByteArrayOutputStream();
            DataOutputStream d = new DataOutputStream(ihdr);
            d.writeInt(w);
            d.writeInt(h);
            d.writeByte(8);  // 8 bits por canal
            d.writeByte(6);  // RGBA
            d.writeByte(0);  // compresión deflate
            d.writeByte(0);  // filtro estándar
            d.writeByte(0);  // sin entrelazado
            writeChunk(out, "IHDR", ihdr.toByteArray());
            writeChunk(out, "IDAT", compressed.toByteArray());
            writeChunk(out, "IEND", new byte[0]);
        }
    }

    private static void writeChunk(DataOutputStream out, String type, byte[] data) throws IOException {
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeInt(data.length);
        out.write(typeBytes);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.writeInt((int) crc.getValue());
    }
}
