package kr.co.daeun.avenstudiobe.integration.google;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConditionalOnProperty(
        name = "google.sheets.enabled",
        havingValue = "true"
)
public class GoogleSheetsConfig {

    @Bean
    public Sheets sheets() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
                .createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY));

        return new Sheets.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                new HttpCredentialsAdapter(credentials)
        )
                .setApplicationName("aven-studio")
                .build();
    }
}