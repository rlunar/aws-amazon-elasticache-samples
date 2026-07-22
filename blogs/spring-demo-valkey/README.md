# Integrate your Spring Boot application with Amazon ElastiCache using Spring Data Valkey

This repository provides the demo code for the blog post https://aws.amazon.com/blogs/database/integrate-your-spring-boot-application-with-amazon-elasticache-using-spring-data-valkey.

Spring Framework supports transparently implementing caching in an application by providing an abstraction layer. The following code demonstrates a simple example of adding caching to a method by including the `@Cacheable` annotation. Before invoking the `getCacheableValue` method, Spring Framework looks for an entry in a cache named `myTestCache` that matches the `myKey` argument. If an entry is found, the content in the cache is immediately returned to the caller, and the method is not invoked. Otherwise, the method is invoked, and the cache is updated before returning the value.

```java
import org.springframework.stereotype.Component;
import org.springframework.cache.annotation.Cacheable;

@Component
public class CacheableComponent {

    @Cacheable("myTestCache")
    public String getCacheableValue(String myKey) {
        // return a value, likely by performing an expensive operation
    }
}
```

To implement caching using Valkey, add the following dependencies to the project's Maven POM file:

```xml
<dependency>
    <groupId>io.valkey.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-valkey</artifactId>
    <version>${spring-data-valkey.version}</version>
</dependency>
<dependency>
    <groupId>io.valkey</groupId>
    <artifactId>valkey-glide</artifactId>
    <classifier>${os.detected.classifier}</classifier>
    <version>${valkey-glide.version}</version>
</dependency>
```

The `spring-boot-starter-data-valkey` dependency adds integration libraries between Spring and Valkey.

The `valkey-glide` dependency provides the high-performance Valkey GLIDE client driver. Valkey GLIDE requires platform-specific native libraries. The `os-maven-plugin` build extension is used to resolve `${os.detected.classifier}` automatically. Add the following to your Maven POM build section:

```xml
<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
</build>
```

Next, add a configuration class to declare that all caches will exist in Valkey:

```java
import io.valkey.springframework.data.valkey.cache.ValkeyCacheManager;
import io.valkey.springframework.data.valkey.connection.ValkeyConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ValkeyConfig {

    @Bean
    public ValkeyCacheManager cacheManager(ValkeyConnectionFactory connectionFactory) {
        return ValkeyCacheManager.create(connectionFactory);
    }
}
```

For configurable values, the Spring Framework `application.properties` file is updated. In the following example, the endpoint address of a Serverless ElastiCache cache is provided:

```
spring.data.valkey.host=cache1-XXXXX.serverless.euw2.cache.amazonaws.com
```

All ElastiCache serverless caches have in-transit encryption enabled. To configure in-transit encryption, we also add:

```
spring.data.valkey.ssl.enabled=true
```

The demo code provided implements this in a simple AWS Command Line Interface (AWS CLI) application. We demonstrate how to build and run this application in the next sections.

## Prerequisites

You will build and run the demo application on an Amazon Elastic Compute Cloud (Amazon EC2) Linux instance, running Linux from AWS. To create an EC2 instance and connect to it using Session Manager, a capability of AWS Systems Manager, refer to Connect to an Amazon EC2 instance by using Session Manager. After you create the instance, note the following information:

- The IDs of the subnets for the virtual private cloud (VPC) your EC2 instance lives in
- The ID of the security group assigned to the instance
- The ID of the EC2 instance

To build the application, you must have the following prerequisites:

- *Java 17* – To install the Java Development Kit (JDK) 17, run `sudo yum install -y java-17-amazon-corretto-devel` on your EC2 instance
- *Maven* – To install Apache Maven, run `sudo yum install -y apache-maven` on your EC2 instance

## Create ElastiCache Serverless cache

We use the ElastiCache Serverless option because it allows you to create a cache in under a minute and instantly scale capacity based on application traffic patterns. We use the Valkey engine, which is a high-performance, open source, in-memory key-value datastore.

To create a serverless cache using the AWS CLI, run the following command in AWS CloudShell, replacing `<your VPC subnet IDs>` with a comma-separated list of the subnet IDs for the VPC containing your EC2 instance:

```
aws elasticache create-serverless-cache \
--serverless-cache-name spring-boot-demo \
--engine valkey \
--subnet-ids <your VPC subnet IDs>
```

Obtain and note the endpoint address for the cache:

```
aws elasticache describe-serverless-caches \
--serverless-cache-name spring-boot-demo \
--query "ServerlessCaches[0].Endpoint.Address"
```

The cache will have a security group. Obtain and note this security group ID:

```
aws elasticache describe-serverless-caches \
--serverless-cache-name spring-boot-demo \
--query "ServerlessCaches[0].SecurityGroupIds"
```

Your EC2 instance and ElastiCache cache exist in the same VPC. To allow access to the cache from the EC2 instance, you must permit this in the associated ElastiCache security group. Add a rule permitting access to port 6379 from the EC2 instance security group:

```
aws ec2 authorize-security-group-ingress \
    --group-id <elasticache security group> \
    --protocol tcp \
    --port 6379 \
    --source-group <ec2 instance security group>
```

## Download and run the demo application

On your EC2 instance, run the following commands:

```
git clone https://github.com/aws-samples/amazon-elasticache-samples.git
cd blogs/spring-demo-valkey
```

Using your preferred editor on the Linux instance, update the `src/main/resources/application.properties` file to include the endpoint address for the `spring-boot-demo` cache. For example:

```
spring.data.valkey.host=spring-boot-demo-XXXXX.serverless.euw2.cache.amazonaws.com
```

Now run the demo application with the following command:

```
mvn spring-boot:run
```

The demo application will build and run. You will see output on the console.

The output shows that for 100 attempts to invoke the getCacheableValue method, the first was a cache miss, causing the method to be invoked. The following 99 attempts were cache hits, returning the value from the cache without invoking the method. You can run the demo application again and see that there are now 100 cache hits and 0 misses (the cache is still populated from the previous run).

## Why Spring Data Valkey

While `spring-boot-starter-data-redis` works seamlessly with Valkey (Valkey is wire-protocol compatible with Redis OSS), Spring Data Valkey offers several advantages including native support for AWS IAM Authentication, OpenTelemetry, and AZ affinity. We cover implementing each of these in the following sections.

## AWS IAM authentication

Spring Data Valkey's GLIDE driver includes native support for AWS IAM authentication when connecting to ElastiCache or MemoryDB. This means your application can use standard IAM roles and policies to control cache access, instead of storing credentials in configuration files or a secrets manager.

To use IAM authentication, first enable IAM authentication on your ElastiCache cache, then configure your `application.properties`:

```
spring.data.valkey.valkeyglide.iam-auth.enabled=true
spring.data.valkey.valkeyglide.iam-auth.region=eu-west-2
spring.data.valkey.valkeyglide.iam-auth.user-id=default
```

With this configuration, Spring Data Valkey automatically:

- Generates IAM authentication credentials using the AWS SDK credential chain
- Authenticates to ElastiCache using the generated credentials
- Refreshes the credentials before expiration

## Availability Zone (AZ) affinity

When running a multi-AZ ElastiCache node-based cache with replicas, read requests can be routed to any replica, including those in a different Availability Zone to your application. This cross-AZ traffic incurs standard data transfer charges and adds network latency.

Valkey GLIDE's AZ affinity feature can route read requests to replicas in the same AZ as your application. Enable this in your Spring Data Valkey `application.properties`:

```
spring.data.valkey.valkeyglide.read-from=AZ_AFFINITY
spring.data.valkey.valkeyglide.client-az=eu-west-2a
```

The `spring.data.valkey.valkeyglide.read-from` parameter supports four read strategies. Choose the strategy best suited to your application's needs.

The `spring.data.valkey.valkeyglide.client-az` parameter must be set to the AZ hosting the application. For many customers this should be determined at runtime to support dynamic application deployment. There are two approaches to achieve this:

1. Establish the AZ outside of the application and set an environment variable before the application is run. The parameter can then consume this environment variable:

```
spring.data.valkey.valkeyglide.client-az=${currentAZ}
```

2. Add a configuration bean to the application to establish the AZ. In this case, remove the parameter from `application.properties`. The example code in this repository demonstrates this approach.

## OpenTelemetry observability

Valkey GLIDE has built-in OpenTelemetry support that can be enabled via `application.properties`:

```
spring.data.valkey.valkey-glide.open-telemetry.enabled=true
spring.data.valkey.valkey-glide.open-telemetry.traces-endpoint=http://localhost:4318/v1/traces
spring.data.valkey.valkey-glide.open-telemetry.metrics-endpoint=http://localhost:4318/v1/metrics
```

## Migrating from Spring Data Redis

If you have an existing application using Spring Data Redis, migrating to Spring Data Valkey is straightforward. The key changes are:

- *Update dependencies* – Replace `spring-boot-starter-data-redis` with `spring-boot-starter-data-valkey` and add the `valkey-glide` dependency
- *Update configuration properties* – Change `spring.data.redis.*` to `spring.data.valkey.*`
- *Update imports* – Change `org.springframework.data.redis.*` to `io.valkey.springframework.data.valkey.*`

## Cleaning up

To avoid incurring future costs, you can delete the chargeable resources created as part of this post.

Delete the ElastiCache serverless cache:

```
aws elasticache delete-serverless-cache --serverless-cache-name spring-boot-demo
```

Delete the EC2 instance:

```
aws ec2 terminate-instances --instance-ids <your EC2 instance ID>
```
