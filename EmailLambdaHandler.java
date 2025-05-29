import com.opencsv.CSVReader;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class EmailLambdaHandler {

    private final S3Client s3Client = S3Client.create();
    private final SesClient sesClient = SesClient.create();
    private final String bucketName = "ttt-email-marketing";  // Replace with your bucket name

    public void handleRequest() {
        try {
            // Read the CSV file from S3
            GetObjectRequest csvRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key("contacts.csv")
                    .build();

            BufferedReader csvReader = new BufferedReader(new InputStreamReader(
                    s3Client.getObject(csvRequest), StandardCharsets.UTF_8));
            CSVReader reader = new CSVReader(csvReader);

            // Read header
            String[] headers = reader.readNext();

            // Read HTML template from S3
            GetObjectRequest htmlRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key("email_template.html")
                    .build();

            String emailHtml = new String(s3Client.getObject(htmlRequest).readAllBytes(), StandardCharsets.UTF_8);

            String[] line;
            while ((line = reader.readNext()) != null) {
                Map<String, String> contact = new HashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    contact.put(headers[i], line[i]);
                }

                // Replace placeholders
                String personalizedEmail = emailHtml.replace("{{FirstName}}", contact.get("FirstName"));

                // Send email
                SendEmailRequest emailRequest = SendEmailRequest.builder()
                        .source("you@yourdomainname.com")  // Replace with your verified address
                        .destination(Destination.builder()
                                .toAddresses(contact.get("Email"))
                                .build())
                        .message(Message.builder()
                                .subject(Content.builder()
                                        .data("Your Weekly Tiny Tales Mail!")
                                        .charset("UTF-8")
                                        .build())
                                .body(Body.builder()
                                        .html(Content.builder()
                                                .data(personalizedEmail)
                                                .charset("UTF-8")
                                                .build())
                                        .build())
                                .build())
                        .build();

                SendEmailResponse response = sesClient.sendEmail(emailRequest);
                System.out.println("Email sent to " + contact.get("Email") + ": Message ID - " + response.messageId());
            }

        } catch (Exception e) {
            System.err.println("An error occurred: " + e.getMessage());
        }
    }
}
