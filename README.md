# KumuluzEE Config
[![Build Status](https://img.shields.io/travis/kumuluz/kumuluzee-config/master.svg?style=flat)](https://travis-ci.org/kumuluz/kumuluzee-config)

> Configuration extension for the KumuluzEE microservice framework with support for configuration servers, such as etcd and Consul.

KumuluzEE Config is an open-source configuration management extension for the KumuluzEE framework. It extends basic 
configuration framework which is a part of KumuluzEE framework, described 
[here](https://github.com/kumuluz/kumuluzee/wiki/Configuration). It provides support for additional configuration 
sources in addition to environment variables and configuration files. 

KumuluzEE Config follows the idea of an unified configuration API for the framework, and provides additional
configuration sources which can be utilised with a standard KumuluzEE configuration interface. 

KumuluzEE Config has been designed to support modularity with plugable configuration sources. Currently etcd &mdash; a 
highly available distributed key-value store &mdash; is supported to act as a configuration server. Consul Key-Value
store is also supported.
In the future, other data stores and configuration servers will be supported too (contributions are welcome).

## Usage
KumuluzEE defines interfaces for common configuraion management features and two basic configuration sources; 
environment variables and configuration files. To include configuration sources from this project you need to include a 
dependency to an implementation library. You can include etcd implementation by adding the 
following dependency:

```xml
<dependency>
   <artifactId>kumuluzee-config-etcd</artifactId>
   <groupId>com.kumuluz.ee.config</groupId>
   <version>${kumuluzee-config.version}</version>
</dependency>
```

Currently, only API v2 is supported. Future releases will support API v3 in a form of a new KumuluzEE Config module.

You can include Consul implementation by adding the 
following dependency:

```xml
<dependency>
   <artifactId>kumuluzee-config-consul</artifactId>
   <groupId>com.kumuluz.ee.config</groupId>
   <version>${kumuluzee-config.version}</version>
</dependency>
```

**Configuring etcd**

To connect to an etcd cluster, an odd number of etcd hosts must be specified with configuration key `kumuluzee.config
.etcd.hosts` in format 
`'http://192.168.99.100:2379,http://192.168.99.101:2379,http://192.168.99.102:2379'`.

Etcd can be configured to support user authentication and client-to-server transport security with HTTPS. To access 
authentication-enabled etcd host, username and password have to be defined with configuration keys 
`kumuluzee.config.etcd.username` and `kumuluzee.config.etcd.password`. To enable transport security, follow 
https://coreos.com/etcd/docs/latest/op-guide/security.html To access HTTPS-enabled etcd host, PEM certificate string
have to be defined with configuration key `kumuluzee.config.etcd.ca`.

**Configuring Consul**

KumuluzEE Config Consul automatically connects to the local agent with no additional configuration required.

**Configuration source priorities**

Included source acts as any other configuration source. It has the second highest priority, which means that properties 
from etcd override properties from configuration files and can be overwritten with properties from environmental 
variables. Future releases will introduce configurable priorities of configuration sources.

**Configuration properties inside etcd**

Configuration properties are stored in etcd key/value store. 

Key names are automatically parsed from KumuluzEE to etcd format (e.g. `environments.dev.name` -> 
`environments/dev/name`).

Configuration properties are in etcd stored in a dedicated namespace, which is automatically generated from 
configuration key `kumuluzee.env`. Example: `kumuluzee.env: dev` is mapped to namespace 
`environments.dev.services`. Automatic namespace generation can be overwritten with key 
`kumuluzee.config.etcd.namespace`. Example: `kumuluzee.config.etcd.namespace: environments.dev.service`. 

Namespace is used as a first part of the key used for etcd key/value store. Example: with set `kumuluzee.env: dev`, 
field `port` from example bellow is in etcd stored in key `/environments/dev/services/test-service/config/port`.

**Configuration properties inside Consul**

Configuration properties in Consul are stored in a similar way as in etcd.

Since Consul uses the same format as etcd, key names are parsed in similar fashion.

KumuluzEE Config Consul also stores keys in automatically generated dedicated namespaces.

For more details, see section above.

**Retrieving configuration properties**

Configuration can be retrieved the same way as in basic configuration framework. 

Configuration properties can be accessed with `ConfigurationUtil` class. Example:

```java
String keyValue = ConfigurationUtil.getInstance().get("key-name");
```

Configuration properties can be injected into a CDI bean with annotations `@ConfigBundle` and `@ConfigValue`. Example:

```java
@ApplicationScoped
@ConfigBundle("test-service.config")
public class ConfigPropertiesExample {
    
    @ConfigValue("port")
    private Boolean servicePort;
    
    // getter and setter methods
}
```

**Watches**

Since configuration properties in etcd and Consul can be updated during microservice runtime, they have to be
dynamically updated inside the running microservices. This behaviour can be enabled with watches.

Watches can be enabled with annotation parameter `@ConfigValue(watch = true)` or by subscribing to key changes.

If watch is enabled on a field, its value will be dynamically updated on any change in configuration source, as long 
as new value is of a proper type. For example, if value in configuration store, linked to an integer field, is changed 
to a non-integer value, field value will not be updated. Example of enabling watch with annotation:

```java
@ApplicationScoped
@ConfigBundle("test-service.config")
public class ConfigPropertiesExample {
    
    @ConfigValue(watch = true)
    private Boolean servicePort;
    
    // getter and setter methods
}
```

Subscribing to key changes is done with an instance of `ConfigurationUtil` class. Example:

```java
String watchedKey = "test-service.config.maintenance";

ConfigurationUtil.getInstance().subscribe(watchedKey, (String key, String value) -> {

    if (watchedKey == key) {

        if ("true".equals(value.toLowerCase())) {
            log.info("Maintenence mode enabled.");
        } else {
            log.info("Maintenence mode disabled.");
        }

    }

});
```

If the key is not present in configuration server, a value from other configuration sources is returned. Similarly, if
the key is deleted from configuration server, a value from other configuration sources is returned.

**Retry delays**

Etcd and Consul implementations support retry delays on watch connection errors. Since they use increasing exponential
delay, two parameters need to be specified:

- `kumuluzee.config.etcd.start-retry-delay-ms`, which sets the retry delay duration in ms on first error - default: 500
- `kumuluzee.config.etcd.max-retry-delay-ms`, which sets the maximum delay duration in ms on consecutive errors -
default: 900000 (15 min)


**Build the microservice**

Ensure you have JDK 8 (or newer), Maven 3.2.1 (or newer) and Git installed.
    
Build the config library with command:

```bash
    mvn install
```
    
Build archives are located in the modules respected folder `target` and local repository `.m2`.

**Run the microservice**

Use the following command to run the sample from Windows CMD:
```
java -cp target/classes;target/dependency/* com.kumuluz.ee.EeApplication 
```

## Changelog

Recent changes can be viewed on Github on the [Releases Page](https://github.com/kumuluz/kumuluzee/releases)

## Contribute

See the [contributing docs](https://github.com/kumuluz/kumuluzee-config/blob/master/CONTRIBUTING.md)

When submitting an issue, please follow the 
[guidelines](https://github.com/kumuluz/kumuluzee-config/blob/master/CONTRIBUTING.md#bugs).

When submitting a bugfix, write a test that exposes the bug and fails before applying your fix. Submit the test 
alongside the fix.

When submitting a new feature, add tests that cover the feature.

## License

MIT
