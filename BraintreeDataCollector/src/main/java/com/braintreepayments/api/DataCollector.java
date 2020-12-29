package com.braintreepayments.api;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.braintreepayments.api.internal.UUIDHelper;
import com.braintreepayments.api.models.ClientToken;
import com.braintreepayments.api.models.Configuration;
import com.braintreepayments.api.models.PaymentMethodNonce;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

// TODO: Break out into separate PayPalDataCollector and BraintreeDataCollector (Kount) modules to align with iOS
public class DataCollector {

    private static final String DEVICE_SESSION_ID_KEY = "device_session_id";
    private static final String FRAUD_MERCHANT_ID_KEY = "fraud_merchant_id";
    private static final String CORRELATION_ID_KEY = "correlation_id";

    private final BraintreeClient braintreeClient;
    private final PayPalDataCollector payPalDataCollector;
    private final KountDataCollector kountDataCollector;

    public DataCollector(BraintreeClient braintreeClient) {
        this(braintreeClient, new PayPalDataCollector(), new KountDataCollector(braintreeClient));
    }

    public DataCollector(BraintreeClient braintreeClient, PayPalDataCollector payPalDataCollector, KountDataCollector kountDataCollector) {
        this.braintreeClient = braintreeClient;
        this.payPalDataCollector = payPalDataCollector;
        this.kountDataCollector = kountDataCollector;
    }

    /**
     * Collect device information for fraud identification purposes.
     *
     * @param context  Android context
     * @param callback to be called with the device data String to send to Braintree.
     */
    public void collectDeviceData(Context context, BraintreeDataCollectorCallback callback) {
        collectDeviceData(context, null, callback);
    }

    /**
     * Collects device data based on your merchant configuration.
     * <p>
     * We recommend that you call this method as early as possible, e.g. at app launch. If that's too early,
     * call it at the beginning of customer checkout.
     * <p>
     * Use the return value on your server, e.g. with `Transaction.sale`.
     *
     * @param context    Android context
     * @param merchantId Optional - Custom Kount merchant id. Leave blank to use the default.
     * @param callback   callback called with the deviceData string that should be passed into server-side calls, such as `Transaction.sale`.
     */
    public void collectDeviceData(final Context context, final String merchantId, final BraintreeDataCollectorCallback callback) {
        braintreeClient.getConfiguration(context, new ConfigurationCallback() {
            @Override
            public void onResult(@Nullable Configuration configuration, @Nullable Exception error) {
                if (configuration != null) {
                    final JSONObject deviceData = new JSONObject();
                    try {
                        String clientMetadataId = getPayPalClientMetadataId(context);
                        if (!TextUtils.isEmpty(clientMetadataId)) {
                            deviceData.put(CORRELATION_ID_KEY, clientMetadataId);
                        }
                    } catch (JSONException ignored) {
                    }

                    if (configuration.getKount().isEnabled()) {
                        final String id;
                        if (merchantId != null) {
                            id = merchantId;
                        } else {
                            id = configuration.getKount().getKountMerchantId();
                        }

                        final String deviceSessionId = UUIDHelper.getFormattedUUID();
                        kountDataCollector.startDataCollection(context, id, deviceSessionId, new KountDataCollectorCallback() {
                            @Override
                            public void onResult(@Nullable String kountSessionId, @Nullable Exception error) {
                                try {
                                    deviceData.put(DEVICE_SESSION_ID_KEY, deviceSessionId);
                                    deviceData.put(FRAUD_MERCHANT_ID_KEY, id);
                                } catch (JSONException ignored) {
                                }

                                callback.onResult(deviceData.toString(), null);
                            }
                        });
                    } else {
                        callback.onResult(deviceData.toString(), null);
                    }
                } else {
                    callback.onResult(null, error);
                }
            }
        });
    }

    /**
     * Collect PayPal device information for fraud identification purposes.
     *
     * @param context  Android context
     * @param callback callback to be called with the device data String to send to Braintree.
     */
    public void collectPayPalDeviceData(Context context, final BraintreeDataCollectorCallback callback) {
        final JSONObject deviceData = new JSONObject();

        try {
            String clientMetadataId = getPayPalClientMetadataId(context);
            if (!TextUtils.isEmpty(clientMetadataId)) {
                deviceData.put(CORRELATION_ID_KEY, clientMetadataId);
            }
        } catch (JSONException ignored) {
        }
        callback.onResult(deviceData.toString(), null);
    }

    /**
     * Collect device information for fraud identification purposes from PayPal only.
     *
     * @param context A valid {@link Context}
     * @return The client metadata id associated with the collected data.
     */
    public String getPayPalClientMetadataId(Context context) {
        try {
            return payPalDataCollector.getClientMetadataId(context);
        } catch (NoClassDefFoundError ignored) {
        }
        return "";
    }

    void collectRiskData(final Context context, @NonNull final PaymentMethodNonce paymentMethodNonce) {
        braintreeClient.getConfiguration(context, new ConfigurationCallback() {
            @Override
            public void onResult(@Nullable Configuration configuration, @Nullable Exception error) {
                if (configuration != null) {
                    if (configuration.getCardConfiguration().isFraudDataCollectionEnabled()) {
                        HashMap<String, String> additionalProperties = new HashMap<>();
                        additionalProperties.put("rda_tenant", "bt_card");
                        additionalProperties.put("mid", configuration.getMerchantId());

                        if (braintreeClient.getAuthorization() instanceof ClientToken) {
                            String customerId = ((ClientToken) braintreeClient.getAuthorization()).getCustomerId();
                            if (customerId != null) {
                                additionalProperties.put("cid", customerId);
                            }
                        }

                        PayPalDataCollectorRequest request = new PayPalDataCollectorRequest()
                                .setApplicationGuid(payPalDataCollector.getPayPalInstallationGUID(context))
                                .setClientMetadataId(paymentMethodNonce.getNonce())
                                .setDisableBeacon(true)
                                .setAdditionalData(additionalProperties);

                        payPalDataCollector.getClientMetadataId(context, request);
                    }
                }
            }
        });
    }
}
