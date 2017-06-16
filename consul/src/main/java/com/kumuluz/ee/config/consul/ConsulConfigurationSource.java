package com.kumuluz.ee.config.consul;

import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.configuration.utils.ConfigurationDispatcher;
import com.kumuluz.ee.configuration.utils.ConfigurationUtil;
import com.orbitz.consul.Consul;
import com.orbitz.consul.ConsulException;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.option.QueryOptions;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.net.ConnectException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Util class for getting and setting configuration properties for Consul Key-Value store.
 *
 * @author Jan Meznarič
 */
public class ConsulConfigurationSource implements ConfigurationSource {

    private static final Logger log = Logger.getLogger(ConsulConfigurationSource.class.getName());

    private ConfigurationDispatcher configurationDispatcher;

    private Consul consul;
    private KeyValueClient kvClient;

    private String namespace;
    private int retryDelay;


    @Override
    public void init(ConfigurationDispatcher configurationDispatcher) {

        ConfigurationUtil configurationUtil = ConfigurationUtil.getInstance();

        String env = configurationUtil.get("kumuluzee.env").orElse(null);
        if (env != null && !env.isEmpty()) {
            this.namespace = "environments." + env + ".services";
        } else {
            this.namespace = "environments.dev.services";
        }

        String consulNamespace = configurationUtil.get("kumuluzee.config.consul.namespace").orElse(null);
        if (consulNamespace != null && !consulNamespace.isEmpty()) {
            this.namespace = consulNamespace;
        }

        this.retryDelay = configurationUtil.getInteger("kumuluzee.config.consul.retry-delay-ms").orElse(1000);

        this.configurationDispatcher = configurationDispatcher;

        consul = Consul.builder().withPing(false).build();

        try {
            consul.agentClient().ping();
        } catch (ConsulException e) {
            log.severe("Cannot ping consul agent: " + e.getLocalizedMessage());
        }

        kvClient = consul.keyValueClient();

        log.info("Consul configuration source successfully initialised.");
    }

    @Override
    public Optional<String> get(@Nonnull String key) {

        key = this.namespace + "." + key;

        Optional<String> value = Optional.empty();

        try {
            value = kvClient.getValueAsString(parseKeyNameForConsul(key)).transform(java.util.Optional::of).or(java.util
                    .Optional.empty());
        } catch (ConsulException e) {
            log.severe("Consul exception: " + e.getLocalizedMessage());
        }

        return value;

    }

    @Override
    public Optional<Boolean> getBoolean(@Nonnull String key) {

        Optional<String> value = get(key);

        return value.map(Boolean::valueOf);
    }

    @Override
    public Optional<Integer> getInteger(@Nonnull String key) {

        Optional<String> value = get(key);

        if (value.isPresent()) {
            try {
                return Optional.of(Integer.valueOf(value.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Double> getDouble(@Nonnull String key) {

        Optional<String> value = get(key);

        if (value.isPresent()) {
            try {
                return Optional.of(Double.valueOf(value.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Float> getFloat(@Nonnull String key) {

        Optional<String> value = get(key);

        if (value.isPresent()) {
            try {
                return Optional.of(Float.valueOf(value.get()));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }

        } else {
            return Optional.empty();
        }
    }

    @Override
    public Optional<Integer> getListSize(String key) {
        return Optional.empty();
    }

    @Override
    public void watch(String key) {

        String fullKey = this.namespace + "." + key;

        log.info("Initializing watcher for key: " + parseKeyNameForConsul(fullKey));

        ConsulResponseCallback<com.google.common.base.Optional<Value>> callback = new ConsulResponseCallback<com
                .google.common.base.Optional<Value>>() {

            AtomicReference<BigInteger> index = new AtomicReference<>(new BigInteger("0"));

            @Override
            public void onComplete(ConsulResponse<com.google.common.base.Optional<Value>> consulResponse) {

                if (consulResponse.getResponse().isPresent()) {

                    Value v = consulResponse.getResponse().get();

                    com.google.common.base.Optional<String> valueOpt = v.getValueAsString();

                    if(valueOpt.isPresent() && configurationDispatcher != null && index.get() != null &&
                            !index.get().equals(consulResponse.getIndex())) {
                        log.info("Consul watcher callback for key " + parseKeyNameForConsul(fullKey) + " invoked. " +
                                "New value: " + valueOpt.get());
                        configurationDispatcher.notifyChange(key, valueOpt.get());
                    }

                }

                index.set(consulResponse.getIndex());

                watch();
            }

            void watch() {
                kvClient.getValue(parseKeyNameForConsul(fullKey), QueryOptions.blockSeconds(9, index.get()).build(),
                        this);
            }

            @Override
            public void onFailure(Throwable throwable) {
                if(throwable instanceof ConnectException) {
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ignored) {
                    }
                } else {
                    log.severe("Watch error: " + throwable.getLocalizedMessage());
                }

                watch();
            }
        };

        kvClient.getValue(parseKeyNameForConsul(fullKey), QueryOptions.blockSeconds(9, new BigInteger("0")).build(),
                callback);

    }

    @Override
    public void set(@Nonnull String key, @Nonnull String value) {
        kvClient.putValue(parseKeyNameForConsul(key), value);
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Boolean value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Integer value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Double value) {
        set(key, value.toString());
    }

    @Override
    public void set(@Nonnull String key, @Nonnull Float value) {
        set(key, value.toString());
    }

    private String parseKeyNameForConsul(String key) {

        return key.replaceAll("\\.", "/");

    }
}