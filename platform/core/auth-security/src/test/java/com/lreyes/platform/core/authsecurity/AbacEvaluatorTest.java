package com.lreyes.platform.core.authsecurity;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AbacEvaluatorTest {

    private final AbacEvaluator evaluator = new AbacEvaluator();

    @Test
    void admin_canApproveAnyAmount() {
        Authentication auth = authWithRoles("admin");
        assertTrue(evaluator.canApprove(auth, new BigDecimal("999999")));
    }

    @Test
    void gestor_canApproveUpToLimit() {
        Authentication auth = authWithRoles("gestor");
        assertTrue(evaluator.canApprove(auth, new BigDecimal("10000")));
        assertTrue(evaluator.canApprove(auth, new BigDecimal("5000")));
    }

    @Test
    void gestor_cannotApproveOverLimit() {
        Authentication auth = authWithRoles("gestor");
        assertFalse(evaluator.canApprove(auth, new BigDecimal("10001")));
        assertFalse(evaluator.canApprove(auth, new BigDecimal("50000")));
    }

    @Test
    void operador_cannotApprove() {
        Authentication auth = authWithRoles("operador");
        assertFalse(evaluator.canApprove(auth, new BigDecimal("100")));
    }

    @Test
    void regionCheck_stubAlwaysAllows() {
        Authentication auth = authWithRoles("operador");
        assertTrue(evaluator.canOperateInRegion(auth, "norte"));
        assertTrue(evaluator.canOperateInRegion(auth, "sur"));
    }

    private Authentication authWithRoles(String... roles) {
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new TestingAuthenticationToken("test-user", null, authorities);
    }
}
