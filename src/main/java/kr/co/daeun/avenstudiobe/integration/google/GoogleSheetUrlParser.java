package kr.co.daeun.avenstudiobe.integration.google;

import kr.co.daeun.avenstudiobe.global.exception.InvalidSheetUrlException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GoogleSheetUrlParser {
    private static final String SPREADSHEET_HOST = "docs.google.com";
    private static final Pattern SPREADSHEET_ID_PATTERN =
            Pattern.compile("/spreadsheets/d/([a-zA-Z0-9-_]+)");

    public static String extractSpreadsheetId(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new InvalidSheetUrlException();
        }

        if (!SPREADSHEET_HOST.equalsIgnoreCase(uri.getHost())) {
            throw new InvalidSheetUrlException();
        }

        String path = uri.getPath();
        Matcher matcher = SPREADSHEET_ID_PATTERN.matcher(path == null ? "" : path);
        if (!matcher.find()) {
            throw new InvalidSheetUrlException();
        }
        return matcher.group(1);
    }
}
