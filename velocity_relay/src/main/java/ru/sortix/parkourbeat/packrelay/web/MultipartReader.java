package ru.sortix.parkourbeat.packrelay.web;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class MultipartReader {
    public static final class Part {
        public String name;
        public String fileName;
        public byte[] data;
    }

    public final Map<String, Part> parts = new HashMap<>();

    public String field(String name) {
        Part part = this.parts.get(name);
        if (part == null || part.data == null) return null;
        return new String(part.data, StandardCharsets.UTF_8).trim();
    }

    public Part file() {
        for (Part part : this.parts.values()) {
            if (part.fileName != null && !part.fileName.isEmpty()) return part;
        }
        return null;
    }

    public static MultipartReader read(InputStream in, String contentType, long limitBytes) throws IOException {
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            throw new IOException("Not a multipart request");
        }
        int index = contentType.toLowerCase().indexOf("boundary=");
        if (index < 0) throw new IOException("No boundary");

        String boundary = contentType.substring(index + 9).trim();
        if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() > 1) {
            boundary = boundary.substring(1, boundary.length() - 1);
        }

        byte[] body = readAll(in, limitBytes);
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);

        MultipartReader result = new MultipartReader();
        int position = indexOf(body, delimiter, 0);
        while (position >= 0) {
            int start = position + delimiter.length;
            if (start + 2 <= body.length && body[start] == '-' && body[start + 1] == '-') break;
            while (start < body.length && (body[start] == '\r' || body[start] == '\n')) start++;

            int headerEnd = indexOf(body, new byte[]{'\r', '\n', '\r', '\n'}, start);
            if (headerEnd < 0) break;

            String headers = new String(body, start, headerEnd - start, StandardCharsets.UTF_8);
            int dataStart = headerEnd + 4;

            int next = indexOf(body, delimiter, dataStart);
            if (next < 0) break;
            int dataEnd = next;
            if (dataEnd - 2 >= dataStart && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') dataEnd -= 2;

            Part part = new Part();
            part.name = extract(headers, "name=\"");
            part.fileName = extract(headers, "filename=\"");
            part.data = new byte[Math.max(0, dataEnd - dataStart)];
            System.arraycopy(body, dataStart, part.data, 0, part.data.length);
            if (part.name != null) result.parts.put(part.name, part);

            position = next;
        }
        return result;
    }

    private static String extract(String headers, String key) {
        int index = headers.indexOf(key);
        if (index < 0) return null;
        int start = index + key.length();
        int end = headers.indexOf('"', start);
        if (end < 0) return null;
        return headers.substring(start, end);
    }

    private static byte[] readAll(InputStream in, long limitBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > limitBytes) throw new IOException("Body too large");
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = Math.max(0, from); i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
