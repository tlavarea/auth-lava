package com.lava.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** Subset of the fields returned by Mailpit's GET /api/v1/messages, used only by tests. */
@JsonIgnoreProperties(ignoreUnknown = true)
record MailpitListResponse(int total, List<MailpitMessage> messages) {}
