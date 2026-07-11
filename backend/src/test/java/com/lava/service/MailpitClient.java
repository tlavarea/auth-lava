package com.lava.service;

import java.util.List;
import org.springframework.web.client.RestClient;

/** Thin wrapper over Mailpit's HTTP API, used only by tests to inspect received mail. */
final class MailpitClient {

    private final RestClient restClient;

    MailpitClient(String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    List<MailpitMessage> listMessages() {
        MailpitListResponse response =
                this.restClient.get().uri("/api/v1/messages").retrieve().body(MailpitListResponse.class);
        return response == null ? List.of() : response.messages();
    }

    MailpitMessageDetail getMessage(String id) {
        return this.restClient.get().uri("/api/v1/message/{id}", id).retrieve().body(MailpitMessageDetail.class);
    }

    void deleteAllMessages() {
        this.restClient.delete().uri("/api/v1/messages").retrieve().toBodilessEntity();
    }
}
