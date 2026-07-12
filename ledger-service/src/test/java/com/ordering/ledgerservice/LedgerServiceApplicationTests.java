package com.ordering.ledgerservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerServiceApplicationTests {

    @Test
    void applicationClassLoads() {
        assertThat(LedgerServiceApplication.class).isNotNull();
    }

}
