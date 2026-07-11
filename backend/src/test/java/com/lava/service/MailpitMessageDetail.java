package com.lava.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Subset of the fields returned by Mailpit's GET /api/v1/message/{id}, used only by tests. */
@JsonIgnoreProperties(ignoreUnknown = true)
record MailpitMessageDetail(@JsonProperty("Text") String text) {}
