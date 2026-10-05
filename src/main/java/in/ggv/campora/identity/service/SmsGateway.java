package in.ggv.campora.identity.service;

/**
 * Outbound SMS. The default implementation only logs; plug in a provider
 * (MSG91, Twilio, AWS SNS …) by adding another bean of this type.
 */
public interface SmsGateway {

    void send(String mobile, String message);
}
