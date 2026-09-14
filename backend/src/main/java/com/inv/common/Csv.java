package com.inv.common;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Minimal CSV (UTF-8 with BOM, Excel friendly) writer / reader. */
public final class Csv {
    private Csv() {
    }

    public static <T> ResponseEntity<byte[]> download(String fileName, String[] headers, List<T> rows, Function<T, Object[]> mapper) {
        StringBuilder sb = new StringBuilder("\uFEFF");
        appendRow(sb, headers);
        for (T row : rows) {
            appendRow(sb, mapper.apply(row));
        }
        byte[] body = sb.toString().getBytes(StandardCharsets.UTF_8);
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
        } catch (IOException e) {
            encoded = "export.csv";
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(body);
    }

    private static void appendRow(StringBuilder sb, Object[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = cells[i] == null ? "" : String.valueOf(cells[i]);
            if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0) {
                v = '"' + v.replace("\"", "\"\"") + '"';
            }
            sb.append(v);
        }
        sb.append("\r\n");
    }

    /** Parses CSV rows (handles quotes, quoted newlines, strips BOM); first row is header. */
    public static List<String[]> read(InputStream in) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Reader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<String> row = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            boolean first = true;
            int ch;
            while ((ch = r.read()) != -1) {
                char c = (char) ch;
                if (first) {
                    first = false;
                    if (c == '\uFEFF') {
                        continue;
                    }
                }
                if (quoted) {
                    if (c == '"') {
                        r.mark(1);
                        int next = r.read();
                        if (next == '"') {
                            cur.append('"');
                        } else {
                            quoted = false;
                            if (next != -1) {
                                r.reset();
                            }
                        }
                    } else {
                        cur.append(c);
                    }
                } else if (c == '"') {
                    quoted = true;
                } else if (c == ',') {
                    row.add(cur.toString().trim());
                    cur.setLength(0);
                } else if (c == '\n') {
                    endRow(rows, row, cur);
                } else if (c != '\r') {
                    cur.append(c);
                }
            }
            endRow(rows, row, cur);
        }
        return rows;
    }

    private static void endRow(List<String[]> rows, List<String> row, StringBuilder cur) {
        row.add(cur.toString().trim());
        cur.setLength(0);
        boolean blank = row.stream().allMatch(String::isEmpty);
        if (!blank) {
            rows.add(row.toArray(new String[0]));
        }
        row.clear();
    }
}
