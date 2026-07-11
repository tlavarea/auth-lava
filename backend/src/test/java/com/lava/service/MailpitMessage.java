package com.lava.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Subset of the fields Mailpit's HTTP API returns for a message, used only by tests. */
@JsonIgnoreProperties(ignoreUnknown = true)
record MailpitMessage(
        @JsonProperty("ID") String id,
        @JsonProperty("From") Address from,
        @JsonProperty("To") List<Address> to,
        @JsonProperty("Subject") String subject) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Address(
            @JsonProperty("Name") String name,
            @JsonProperty("Address") String address) {}
}
