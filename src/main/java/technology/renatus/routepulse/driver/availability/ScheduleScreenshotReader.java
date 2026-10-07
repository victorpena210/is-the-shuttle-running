package technology.renatus.routepulse.driver.availability;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** Local OCR: bounded uploads, decoded image validation, no external service or retained images. */
@Service
public class ScheduleScreenshotReader {
    public record ReadResult(String text, String digest) { }
    private final String executable;
    private final Semaphore slots = new Semaphore(2);
    public ScheduleScreenshotReader(@Value("${app.schedule-ocr.command:tesseract}") String executable) { this.executable = executable; }

    public ReadResult read(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new IllegalArgumentException("Choose a PNG or JPEG screenshot up to 5 MB.");
        if (!slots.tryAcquire()) throw new IllegalArgumentException("Screenshot reading is busy. Try again shortly or enter hours manually.");
        Path directory = null; Process process = null;
        try {
            byte[] bytes = file.getBytes(); BufferedImage image;
            try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(stream);
                if (!readers.hasNext()) throw new IllegalArgumentException("This file is not a readable PNG or JPEG image.");
                var reader = readers.next();
                try {
                    String format = reader.getFormatName().toLowerCase(Locale.US);
                    if (!Set.of("png","jpeg","jpg").contains(format)) throw new IllegalArgumentException("Use a PNG or JPEG screenshot.");
                    reader.setInput(stream);
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 100 || height < 100 || width > 6000 || height > 10000 || (long)width * height > 16000000)
                        throw new IllegalArgumentException("Crop your screenshot to the schedule (100–6,000 pixels wide; at most 16 megapixels).");
                    image = reader.read(0);
                } finally { reader.dispose(); }
            }
            // Flatten transparency and strip all uploaded metadata before invoking native OCR.
            var clean = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            var graphics = clean.createGraphics();
            graphics.setColor(Color.WHITE); graphics.fillRect(0,0,clean.getWidth(),clean.getHeight());
            graphics.drawImage(image,0,0,null); graphics.dispose();
            directory = Files.createTempDirectory("driver-schedule-");
            var input = directory.resolve("schedule.png"); var output = directory.resolve("read.txt");
            ImageIO.write(clean,"png",input.toFile());
            var builder = new ProcessBuilder(executable,input.toString(),"stdout","-l","eng","--psm","6")
                    .redirectOutput(output.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().put("OMP_THREAD_LIMIT","1");
            process = builder.start();
            if (!process.waitFor(20,TimeUnit.SECONDS)) {
                process.destroyForcibly(); process.waitFor(2,TimeUnit.SECONDS);
                throw new IllegalArgumentException("Reading took too long. Crop the screenshot or enter hours manually.");
            }
            if (process.exitValue() != 0 || !Files.exists(output) || Files.size(output) > 50000)
                throw new IllegalArgumentException("Could not read this screenshot. Try a clearer crop or enter hours manually.");
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new ReadResult(Files.readString(output),digest);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Screenshot reading is unavailable. Enter your hours manually, or ask your manager to check the OCR installation.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new IllegalArgumentException("Reading was interrupted. Please try again.");
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (directory != null) try (var files = Files.list(directory)) {
                for (Path path : files.toList()) Files.deleteIfExists(path);
                Files.deleteIfExists(directory);
            } catch (IOException ignored) { /* Temporary directory has no public route. */ }
            slots.release();
        }
    }
}
