
package com.fintech.controltower.financial.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Service
public class FxRateService {

    private static final Logger log =
            LoggerFactory.getLogger(FxRateService.class);

    private static final String PROVIDER = "ExchangeRate-API";

    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final int RATE_PRECISION = 16;

    private final RestClient restClient;
    private final Duration maxRateAge;

    private volatile RateSnapshot currentSnapshot;

    public FxRateService(
            RestClient.Builder restClientBuilder,
            @Value("${payment-financial.fx-provider.base-url:https://open.er-api.com/v6/latest}")
            String baseUrl,
            @Value("${payment-financial.fx-provider.max-rate-age-hours:36}")
            long maxRateAgeHours
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .build();

        if (maxRateAgeHours <= 0) {
            throw new IllegalArgumentException(
                    "Maximum FX rate age must be greater than zero."
            );
        }

        this.maxRateAge = Duration.ofHours(maxRateAgeHours);
    }

    @PostConstruct
    public void initialize() {
        refreshSafely();
    }

    @Scheduled(
            fixedDelayString =
                    "${payment-financial.fx-provider.refresh-interval-ms:86400000}",
            initialDelayString =
                    "${payment-financial.fx-provider.refresh-interval-ms:86400000}"
    )
    public void scheduledRefresh() {
        refreshSafely();
    }

    public FxRateQuote getRate(
            String sourceCurrency,
            String destinationCurrency
    ) {
        if (sourceCurrency.equals(destinationCurrency)) {
            return new FxRateQuote(
                    ONE,
                    "SAME_CURRENCY",
                    null
            );
        }

        RateSnapshot snapshot = currentSnapshot;

        if (snapshot == null) {
            throw new IllegalStateException(
                    "No valid FX rate snapshot is currently available."
            );
        }

        Instant now = Instant.now();

        if (snapshot.updatedAt().isAfter(now.plus(Duration.ofMinutes(5)))
                || snapshot.updatedAt().isBefore(now.minus(maxRateAge))) {
            throw new IllegalStateException(
                    "The available FX rate snapshot has expired."
            );
        }

        String key = sourceCurrency + "_" + destinationCurrency;

        BigDecimal rate = snapshot.rates().get(key);

        if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "No valid FX rate available for " + key
            );
        }

        return new FxRateQuote(
                rate,
                snapshot.provider(),
                LocalDateTime.ofInstant(
                        snapshot.updatedAt(),
                        ZoneOffset.UTC
                )
        );
    }

    public synchronized void refreshRates() {

        Map<String, BigDecimal> refreshedRates = new HashMap<>();

        Instant oldestUpdate = null;

        // INR base: INR -> USD, GBP and SGD provider quotes.
        ExchangeRateResponse inrResponse = fetchRates("INR");

        oldestUpdate = updateOldestTimestamp(
                oldestUpdate,
                inrResponse
        );

        addInvertedRate(
                refreshedRates,
                inrResponse,
                "INR",
                "USD"
        );

        addInvertedRate(
                refreshedRates,
                inrResponse,
                "INR",
                "GBP"
        );

        addInvertedRate(
                refreshedRates,
                inrResponse,
                "INR",
                "SGD"
        );

        // USD base: USD -> GBP provider quote.
        ExchangeRateResponse usdResponse = fetchRates("USD");

        oldestUpdate = updateOldestTimestamp(
                oldestUpdate,
                usdResponse
        );

        addInvertedRate(
                refreshedRates,
                usdResponse,
                "USD",
                "GBP"
        );

        // EUR base: EUR -> USD provider quote.
        ExchangeRateResponse eurResponse = fetchRates("EUR");

        oldestUpdate = updateOldestTimestamp(
                oldestUpdate,
                eurResponse
        );

        addInvertedRate(
                refreshedRates,
                eurResponse,
                "EUR",
                "USD"
        );

        validateSnapshotTimestamp(oldestUpdate);

        if (refreshedRates.size() != 5) {
            throw new IllegalStateException(
                    "FX provider did not return all five required currency pairs."
            );
        }

        // Replace the snapshot only after every rate is validated.
        currentSnapshot = new RateSnapshot(
                Map.copyOf(refreshedRates),
                oldestUpdate,
                PROVIDER
        );

        log.info(
                "FX rates refreshed successfully. Pairs: {}, Provider update: {}",
                refreshedRates.size(),
                oldestUpdate
        );
    }

    private ExchangeRateResponse fetchRates(String baseCurrency) {

        ExchangeRateResponse response = restClient.get()
                .uri("/{baseCurrency}", baseCurrency)
                .retrieve()
                .body(ExchangeRateResponse.class);

        if (response == null
                || !"success".equalsIgnoreCase(response.result())
                || !baseCurrency.equalsIgnoreCase(response.baseCode())
                || response.rates() == null
                || response.timeLastUpdateUnix() <= 0) {

            throw new IllegalStateException(
                    "Invalid FX provider response for base currency "
                            + baseCurrency
            );
        }

        return response;
    }

    private void addInvertedRate(
            Map<String, BigDecimal> rates,
            ExchangeRateResponse response,
            String sourceCurrency,
            String destinationCurrency
    ) {
        BigDecimal providerRate =
                response.rates().get(destinationCurrency);

        if (providerRate == null
                || providerRate.compareTo(BigDecimal.ZERO) <= 0) {

            throw new IllegalStateException(
                    "Missing or invalid provider rate for "
                            + sourceCurrency + " -> " + destinationCurrency
            );
        }

        // Provider: 1 source = X destination.
        // Application: 1 destination = X source.
        BigDecimal applicationRate = ONE.divide(
                providerRate,
                RATE_PRECISION,
                RoundingMode.HALF_UP
        );

        rates.put(
                sourceCurrency + "_" + destinationCurrency,
                applicationRate
        );
    }

    private Instant updateOldestTimestamp(
            Instant currentOldest,
            ExchangeRateResponse response
    ) {
        Instant providerUpdate = Instant.ofEpochSecond(
                response.timeLastUpdateUnix()
        );

        if (currentOldest == null
                || providerUpdate.isBefore(currentOldest)) {
            return providerUpdate;
        }

        return currentOldest;
    }

    private void validateSnapshotTimestamp(Instant updatedAt) {

        Instant now = Instant.now();

        if (updatedAt.isAfter(now.plus(Duration.ofMinutes(5)))) {
            throw new IllegalStateException(
                    "FX provider returned a future update timestamp."
            );
        }

        if (updatedAt.isBefore(now.minus(maxRateAge))) {
            throw new IllegalStateException(
                    "FX provider returned an expired rate snapshot."
            );
        }
    }

    private void refreshSafely() {
        try {
            refreshRates();
        } catch (Exception exception) {
            log.error(
                    "FX rate refresh failed. Existing snapshot will be retained.",
                    exception
            );
        }
    }

    public record FxRateQuote(
            BigDecimal rate,
            String provider,
            LocalDateTime timestamp
    ) {}

    private record RateSnapshot(
            Map<String, BigDecimal> rates,
            Instant updatedAt,
            String provider
    ) {}

    private record ExchangeRateResponse(
            String result,

            @JsonProperty("base_code")
            String baseCode,

            @JsonProperty("time_last_update_unix")
            long timeLastUpdateUnix,

            Map<String, BigDecimal> rates
    ) {}
}
