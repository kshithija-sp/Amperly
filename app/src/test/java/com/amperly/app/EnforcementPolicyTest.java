package com.amperly.app;

import com.amperly.app.core.common.AppConstants;
import com.amperly.app.engine.admin.EnforcementPolicyEngine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EnforcementPolicyTest {

    @Test
    public void unverifiedAccusationDoesNotPunishTheHost() {
        EnforcementPolicyEngine.EnforcementDecision decision =
                EnforcementPolicyEngine.forUnverifiedAccusation();

        assertEquals(AppConstants.ENFORCEMENT_NONE, decision.getAction());
        assertEquals(0.0, decision.getFineAmount(), 0.001);
        assertFalse(decision.isSuspendStation());
        assertFalse(decision.isBanStation());
        assertTrue(decision.getReason().toLowerCase().contains("not yet supported by evidence"));
    }

    @Test
    public void insufficientEvidenceHoldsThePayoutWithoutPenalty() {
        EnforcementPolicyEngine.EnforcementDecision decision =
                EnforcementPolicyEngine.decide(1, false, false);

        assertEquals(AppConstants.ENFORCEMENT_NONE, decision.getAction());
        assertTrue(decision.isFreezePayout());
        assertFalse(decision.isSuspendStation());
    }

    @Test
    public void enforcementEscalatesFromWarningToFineToSuspensionToBan() {
        EnforcementPolicyEngine.EnforcementDecision first = EnforcementPolicyEngine.decide(1, true, false);
        assertEquals(AppConstants.ENFORCEMENT_WARNING, first.getAction());
        assertEquals(0.0, first.getFineAmount(), 0.001);

        EnforcementPolicyEngine.EnforcementDecision second = EnforcementPolicyEngine.decide(2, true, false);
        assertEquals(AppConstants.ENFORCEMENT_FINE, second.getAction());
        assertTrue(second.getFineAmount() > 0);

        EnforcementPolicyEngine.EnforcementDecision third = EnforcementPolicyEngine.decide(3, true, false);
        assertEquals(AppConstants.ENFORCEMENT_SUSPENSION, third.getAction());
        assertTrue(third.isSuspendStation());
        assertFalse(third.isBanStation());

        EnforcementPolicyEngine.EnforcementDecision fourth = EnforcementPolicyEngine.decide(4, true, false);
        assertEquals(AppConstants.ENFORCEMENT_BAN, fourth.getAction());
        assertTrue(fourth.isBanStation());
    }

    @Test
    public void severeFraudIsSuspendedAndBannedImmediately() {
        EnforcementPolicyEngine.EnforcementDecision decision =
                EnforcementPolicyEngine.decide(1, true, true);

        assertEquals(AppConstants.ENFORCEMENT_BAN, decision.getAction());
        assertTrue(decision.isSuspendStation());
        assertTrue(decision.isBanStation());
        assertTrue(decision.isFreezePayout());
    }

    @Test
    public void policyStepsExplainTheProcessToAdmins() {
        EnforcementPolicyEngine.EnforcementDecision decision = EnforcementPolicyEngine.decide(2, true, false);
        assertTrue(decision.getAudienceSteps().size() >= 4);
        assertTrue(decision.getAudienceSteps().stream().anyMatch(step ->
                step.toLowerCase().contains("host is given an opportunity to respond")));
    }
}
