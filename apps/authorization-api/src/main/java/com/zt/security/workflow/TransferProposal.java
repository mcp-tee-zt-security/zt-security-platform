package com.zt.security.workflow;
import java.math.BigDecimal;

public record TransferProposal(String recipient,BigDecimal amount,String currency){
    public TransferProposal {
        if(recipient==null||recipient.isBlank()||recipient.length()>120||recipient.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("A recipient of 1..120 characters is required");
        recipient=recipient.trim();
        if(amount==null||amount.signum()<=0||amount.compareTo(new BigDecimal("1000000"))>0||amount.stripTrailingZeros().scale()>2)
            throw new IllegalArgumentException("Amount must be positive, at most 1000000 EUR, with at most two decimal places");
        if(!"EUR".equals(currency))throw new IllegalArgumentException("This simulation supports EUR only; no currency conversion is performed");
    }
}
