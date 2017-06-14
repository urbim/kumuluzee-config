package com.kumuluz.ee.config.consul;

import com.kumuluz.ee.configuration.ConfigurationSource;
import com.kumuluz.ee.configuration.utils.ConfigurationDispatcher;
import com.orbitz.consul.Consul;
import com.orbitz.consul.KeyValueClient;
import com.orbitz.consul.async.ConsulResponseCallback;
import com.orbitz.consul.model.ConsulResponse;
import com.orbitz.consul.model.kv.Value;
import com.orbitz.consul.option.QueryOptions;

import javax.annotation.Nonnull;
import java.math.BigInteger;
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


    @Override
    public void init(ConfigurationDispatcher configurationDispatcher) {

        this.configurationDispatcher = configurationDispatcher;

        consul = Consul.builder().build();
        kvClient = consul.keyValueClient();

        log.info("Consul configuration source successfully initialised.");
    }

    @Override
    public Optional<String> get(@Nonnull String key) {

        return kvClient.getValueAsString(parseKeyNameForConsul(key)).transform(java.util.Optional::of).or(java.util
                .Optional.empty());

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

        log.info("Initializing watcher for key: " + parseKeyNameForConsul(key));

        ConsulResponseCallback<com.google.common.base.Optional<Value>> callback = new ConsulResponseCallback<com
                .google.common.base.Optional<Value>>() {

            AtomicReference<BigInteger> index = new AtomicReference<>(null);

            @Override
            public void onComplete(ConsulResponse<com.google.common.base.Optional<Value>> consulResponse) {

                if (consulResponse.getResponse().isPresent()) {

                    Value v = consulResponse.getResponse().get();

                    log.info("Consul watcher callback for key " + parseKeyNameForConsul(key) + "invoked. New value: "
                            + v.getValueAsString().get());

                    if(v.getValueAsString().isPresent() && configurationDispatcher != null) {
                        configurationDispatcher.notifyChange(key, v.getValueAsString().get());
                    }

                }

                index.set(consulResponse.getIndex());

                watch();
            }

            void watch() {
                kvClient.getValue(parseKeyNameForConsul(key), QueryOptions.blockSeconds(9, index.get()).build(),
                        this);
            }

            @Override
            public void onFailure(Throwable throwable) {
                watch();
            }
        };

        kvClient.getValue(parseKeyNameForConsul(key), QueryOptions.blockSeconds(9, new BigInteger("0")).build(),
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
