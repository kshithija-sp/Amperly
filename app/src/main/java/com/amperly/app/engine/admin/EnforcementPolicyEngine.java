package com.amperly.app.engine.admin;

import com.amperly.app.core.common.AppConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Host enforcement policy.
 *
 * The policy is evidence based: an unverified accusation never triggers a penalty. Once an incident
 * is proven, the escalation ladder is warning → fine → suspension → ban, with immediate suspension
 * reserved for severe or fraudulent behaviour.
 */
public final class EnforcementPolicyEngine {

    public static final double DEFAULT_FINE_INR = 500.0;

    public static class EnforcementDecision {
        private final String action;
        private final String reason;
        private final double fineAmount;
        private final boolean suspendStation;
        private final boolean banStation;
        private final boolean freezePayout;
        private final List<String> audienceSteps;
        private final String hostMessage;

        public EnforcementDecision(String action, String reason, double fineAmount,
                                   boolean suspendStation, boolean banStation, boolean freezePayout,
                                   List<String> audienceSteps, String hostMessage) {
            this.action = action;
            this.reason = reason;
            this.fineAmount = fineAmount;
            this.suspendStation = suspendStation;
            this.banStation = banStation;
            this.freezePayout = freezePayout;
            this.audienceSteps = audienceSteps;
            this.hostMessage = hostMessage;
        }

        public String getAction() { return action; }
        public String getReason() { return reason; }
        public double getFineAmount() { return fineAmount; }
        public boolean isSuspendStation() { return suspendStation; }
        public boolean isBanStation() { return banStation; }
        public boolean isFreezePayout() { return freezePayout; }
        public List<String> getAudienceSteps() { return audienceSteps; }
        public String getHostMessage() { return hostMessage; }
    }

    private EnforcementPolicyEngine() {
    }

    public static EnforcementDecision forUnverifiedAccusation() {
        List<String> steps = new ArrayList<>();
        steps.add("Consumer evidence is reviewed before any action is taken.");
        steps.add("No penalty is applied on an unverified accusation alone.");
        return new EnforcementDecision(AppConstants.ENFORCEMENT_NONE,
                "Accusation is not yet supported by evidence.", 0, false, false, false, steps,
                "No action taken. The consumer was told what evidence is needed.");
    }

    /**
     * @param provenIncidentsIncludingThis total proven incidents for this host once the current
     *                                      dispute is upheld
     * @param evidenceSufficient            whether the uploaded meter evidence supports the claim
     * @param severeFraud                   deliberate meter tampering or fabricated billing
     */
    public static EnforcementDecision decide(int provenIncidentsIncludingThis,
                                            boolean evidenceSufficient,
                                            boolean severeFraud) {
        List<String> steps = baseSteps();
        if (!evidenceSufficient) {
            steps.add("Evidence is insufficient, so the payout is held while the host responds.");
            return new EnforcementDecision(AppConstants.ENFORCEMENT_NONE,
                    "Evidence does not yet support a penalty.", 0, false, false, true, steps,
                    "Please share your meter photographs and session notes so the dispute can be closed.");
        }

        if (severeFraud) {
            steps.add("Severe or fraudulent behaviour results in immediate suspension and a ban.");
            return new EnforcementDecision(AppConstants.ENFORCEMENT_BAN,
                    "Proven severe fraud.", 0, true, true, true, steps,
                    "Your station has been suspended immediately pending a full review.");
        }

        if (provenIncidentsIncludingThis <= 1) {
            steps.add("First proven incident: warning and consumer correction.");
            return new EnforcementDecision(AppConstants.ENFORCEMENT_WARNING,
                    "First proven incident.", 0, false, false, false, steps,
                    "This is a formal warning. The consumer has been refunded or corrected.");
        }
        if (provenIncidentsIncludingThis == 2) {
            steps.add("Second proven incident: warning plus fine.");
            return new EnforcementDecision(AppConstants.ENFORCEMENT_FINE,
                    "Second proven incident.", DEFAULT_FINE_INR, false, false, false, steps,
                    "A fine of ₹" + (int) DEFAULT_FINE_INR + " applies and the consumer has been refunded.");
        }
        if (provenIncidentsIncludingThis == 3) {
            steps.add("Third proven incident: station suspension.");
            return new EnforcementDecision(AppConstants.ENFORCEMENT_SUSPENSION,
                    "Third proven incident.", DEFAULT_FINE_INR, true, false, true, steps,
                    "Your station is suspended until the outstanding issues are resolved.");
        }
        steps.add("Repeated proven incidents: station ban.");
        return new EnforcementDecision(AppConstants.ENFORCEMENT_BAN,
                "Repeated proven incidents.", DEFAULT_FINE_INR, true, true, true, steps,
                "Your station has been banned from the platform.");
    }

    private static List<String> baseSteps() {
        List<String> steps = new ArrayList<>();
        steps.add("Consumer evidence is reviewed.");
        steps.add("The payment or host payout is held temporarily during the review.");
        steps.add("The host is given an opportunity to respond.");
        steps.add("If the host is responsible, the consumer receives a refund or correction.");
        return steps;
    }
}
