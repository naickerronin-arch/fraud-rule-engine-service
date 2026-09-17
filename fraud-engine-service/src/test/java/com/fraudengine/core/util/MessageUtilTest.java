package com.fraudengine.core.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

class MessageUtilTest {

    private MessageUtil messageUtil;

    @BeforeEach
    void setUp() {
        StaticMessageSource messageSource = new StaticMessageSource();
        messageSource.addMessage("transaction.not.found", Locale.ENGLISH, "001|Transaction not found");
        messageSource.addMessage("no.code", Locale.ENGLISH, "Something went wrong");

        messageUtil = new MessageUtil(messageSource);
    }

    // ========== getError() Tests ==========

    @Test
    void shouldSplitTheCodeFromTheMessage_whenTheMessageHasBoth() {
        MessageUtil.ErrorDetail error = messageUtil.getError("transaction.not.found");

        assertThat(error.getCode()).isEqualTo("001");
        assertThat(error.getMessage()).isEqualTo("Transaction not found");
    }

    @Test
    void shouldUseTheGenericCode_whenTheMessageHasNoCode() {
        MessageUtil.ErrorDetail error = messageUtil.getError("no.code");

        assertThat(error.getCode()).isEqualTo("999");
        assertThat(error.getMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void shouldReturnAGenericError_whenTheKeyIsUnknown() {
        MessageUtil.ErrorDetail error = messageUtil.getError("does.not.exist");

        assertThat(error.getCode()).isEqualTo("999");
        assertThat(error.getMessage()).isEqualTo("An unexpected error occurred");
    }
}
