package kr.co.daeun.avenstudiobe.integration.google;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import kr.co.daeun.avenstudiobe.global.exception.SheetAccessDeniedException;
import kr.co.daeun.avenstudiobe.global.exception.SheetReadException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class GoogleSheetsReader {

    private final Sheets sheets;

    public List<List<Object>> read(String spreadsheetId, String range) {
        try {
            ValueRange response = sheets.spreadsheets()
                    .values()
                    .get(spreadsheetId, range)
                    .execute();

            List<List<Object>> values = response.getValues();
            return values == null ? Collections.emptyList() : values;

        } catch (GoogleJsonResponseException e) {
            int statusCode = e.getStatusCode();
            if (statusCode == 404) {
                throw new SheetReadException(e);
            }
            if (statusCode == 403) {
                throw new SheetAccessDeniedException();
            }
            throw new SheetReadException(e);
        } catch (IOException e) {
            throw new SheetReadException(e);
        }
    }
}
