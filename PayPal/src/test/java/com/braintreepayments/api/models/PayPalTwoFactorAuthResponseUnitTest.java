package com.braintreepayments.api.models;

import com.braintreepayments.testutils.Fixtures;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.modules.junit4.PowerMockRunner;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

@RunWith(PowerMockRunner.class)
public class PayPalTwoFactorAuthResponseUnitTest {

    @Test
    public void fromJson_buildsResponse_withAllParams() throws JSONException {
       PayPalTwoFactorAuthResponse response = PayPalTwoFactorAuthResponse
               .fromJson(Fixtures.PAYMENT_METHODS_HERMES_PAYMENT_RESOURCE_RESPONSE_WITH_AUTHENTICATE_URL,
                       "fake-authorization-fingerprint");

        assertEquals("fake-authenticate-url", response.getAuthenticateUrl());
        assertEquals("fake-token", response.getPaymentToken());
        assertEquals("fake-redirect-url", response.getRedirectUrl());
        assertEquals("authorize", response.getResourceIntent());
    }

    @Test
    public void fromJson_buildsResponse_withoutAuthenticateURL() throws JSONException {
        PayPalTwoFactorAuthResponse response = PayPalTwoFactorAuthResponse
                .fromJson(Fixtures.PAYMENT_METHODS_HERMES_PAYMENT_RESOURCE_RESPONSE_WITHOUT_AUTHENTICATE_URL,
                        "fake-authorization-fingerprint");

        assertNull(response.getAuthenticateUrl());
    }

    @Test
    public void toJson_returnsFormattedJson_withAllParams() throws JSONException {
        PayPalTwoFactorAuthResponse response = PayPalTwoFactorAuthResponse
                .fromJson(Fixtures.PAYMENT_METHODS_HERMES_PAYMENT_RESOURCE_RESPONSE_WITH_AUTHENTICATE_URL,
                        "fake-authorization-fingerprint");

        JSONObject json = new JSONObject(response.toJson("correlationId"));
        assertEquals("fake-authorization-fingerprint", json.getString("authorization_fingerprint"));

        JSONObject paypalAccount = json.getJSONObject("paypal_account");
        assertEquals("fake-token", paypalAccount.getString("payment_token"));
        assertEquals("correlationId", paypalAccount.getString("correlation_id"));

        JSONObject options = paypalAccount.getJSONObject("options");
        assertFalse(options.getBoolean("sca_authentication_complete"));
    }

    @Test
    public void toJson_returnsFormattedJson_withoutAuthenticateURL() throws JSONException {
        PayPalTwoFactorAuthResponse response = PayPalTwoFactorAuthResponse
                .fromJson(Fixtures.PAYMENT_METHODS_HERMES_PAYMENT_RESOURCE_RESPONSE_WITHOUT_AUTHENTICATE_URL,
                        "fake-authorization-fingerprint");

        JSONObject json = new JSONObject(response.toJson("correlationId"));
        JSONObject paypalAccountOptions = json.getJSONObject("paypal_account").getJSONObject("options");

        assertTrue(paypalAccountOptions.getBoolean("sca_authentication_complete"));
    }
}
