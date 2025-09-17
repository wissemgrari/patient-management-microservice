package com.pm.stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Token;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

public class LocalStack extends Stack {

  private final Vpc vpc;
  private final Cluster ecsCluster;

  public LocalStack(final App scope, final String id, final StackProps props) {
    super(scope, id, props);
    this.vpc = createVpc();

    DatabaseInstance authServiceDb = createDatabaseInstance("AuthServiceDB", "auth-service-db");

    DatabaseInstance patientServiceDb = createDatabaseInstance("PatientServiceDB",
        "patient-service-db");

    CfnHealthCheck authDBHealthCheck = createDBHealthCheck(authServiceDb,
        "AuthServiceDBHealthCheck");

    CfnHealthCheck patientDBHealthCheck = createDBHealthCheck(patientServiceDb,
        "PatientServiceDBHealthCheck");

    CfnCluster mskCluster = createMskCluster();

    this.ecsCluster = createEcsCluster();

    FargateService authService = createFargateService("AuthService",
        "auth-service",
        List.of(4005),
        authServiceDb,
        Map.of("JWT_SECRET", "cG9vcmluZGl2aWR1YWxjaGVtaWNhbHJheXNtYWluaG90bmVlZHNjb3BwZXJzcXVhcmU="));
    authService.getNode().addDependency(authDBHealthCheck);
    authService.getNode().addDependency(authServiceDb);

    FargateService billingService = createFargateService("BillingService",
        "billing-service",
        List.of(4001, 9001),
        null,
        null);

    FargateService analyticsService = createFargateService("AnalyticsService",
        "analytics-service",
        List.of(4002),
        null,
        null);
    analyticsService.getNode().addDependency(mskCluster);

    FargateService patientService = createFargateService("PatientService",
        "patient-service",
        List.of(4000),
        patientServiceDb,
        Map.of("BILLING_SERVICE_ADDRESS", "host.docker.internal",
            "BILLING_SERVICE_PORT", "9001"));
    patientService.getNode().addDependency(patientServiceDb);
    patientService.getNode().addDependency(patientDBHealthCheck);
    patientService.getNode().addDependency(billingService);
    patientService.getNode().addDependency(mskCluster);

  }

  private Vpc createVpc() {
    return Vpc.Builder.create(this, "PatientManagementVPC")
        .vpcName("PatientManagementVPC")
        .maxAzs(2)
        .build();

  }

  private DatabaseInstance createDatabaseInstance(String id, String dbName) {
    return DatabaseInstance.Builder.create(this, id)
        .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
            .version(PostgresEngineVersion.VER_17_2)
            .build()))
        .vpc(vpc)
        .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
        .allocatedStorage(20)
        .credentials(Credentials.fromGeneratedSecret("admin_user"))
        .databaseName(dbName)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build();
  }

  private CfnHealthCheck createDBHealthCheck(DatabaseInstance db, String id) {
    return CfnHealthCheck.Builder.create(this, id)
        .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
            .type("TCP")
            .port(Token.asNumber(db.getDbInstanceEndpointPort()))
            .ipAddress(db.getDbInstanceEndpointAddress())
            .requestInterval(30)
            .failureThreshold(3)
            .build())
        .build();
  }

  private CfnCluster createMskCluster() {
    return CfnCluster.Builder.create(this, "MskCluster")
        .clusterName("kafka-cluster")
        .kafkaVersion("2.8.0")
        .numberOfBrokerNodes(1)
        .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
            .instanceType("kafka.m5.xlarge")
            .clientSubnets(vpc.getPrivateSubnets()
                .stream()
                .map(ISubnet::getSubnetId)
                .collect(Collectors.toList()))
            .brokerAzDistribution("DEFAULT")
            .build())
        .build();
  }

  private Cluster createEcsCluster() {
    return Cluster.Builder.create(this, "PatientManagementCluster")
        .vpc(vpc)
        .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
            .name("patient-management.local")
            .build())
        .build();
  }

  private FargateService createFargateService(
      String id,
      String imageName,
      List<Integer> ports,
      DatabaseInstance db,
      Map<String, String> additionalEnvVars) {

    FargateTaskDefinition taskDefinition = FargateTaskDefinition.Builder
        .create(this, id + "Task")
        .cpu(256)
        .memoryLimitMiB(512)
        .build();

    ContainerDefinitionOptions.Builder containerOptions = ContainerDefinitionOptions.builder()
        .image(ContainerImage.fromRegistry(imageName))
        .portMappings(ports.stream()
            .map(port -> PortMapping.builder()
                .containerPort(port)
                .hostPort(port).protocol(Protocol.TCP)
                .build())
            .toList())
        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
            .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                .logGroupName("/ecs/" + imageName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .retention(RetentionDays.ONE_DAY)
                .build())
            .streamPrefix(imageName)
            .build()));

    Map<String, String> enVars = new HashMap<>();
    enVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost"
        + ".localstack.cloud:4511, localhost.localstack.cloud:4512");

    if (additionalEnvVars != null) {
      enVars.putAll(additionalEnvVars);
    }

    if (db != null) {
      enVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
          db.getDbInstanceEndpointAddress(),
          db.getDbInstanceEndpointPort(),
          imageName
      ));
      enVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
      enVars.put("SPRING_DATASOURCE_PASSWORD",
          db.getSecret().secretValueFromJson("password").toString());
      enVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
      enVars.put("SPRING_SQL_INIT_MODE", "always");
      enVars.put("SPRING_DATASOURCE_HIKARY_INITIALIZATION_FAIL_TIMEOUT", "60000");
    }

    containerOptions.environment(enVars);
    taskDefinition.addContainer(imageName + "Container", containerOptions.build());

    return FargateService.Builder
        .create(this, id)
        .cluster(ecsCluster)
        .taskDefinition(taskDefinition)
        .assignPublicIp(false)
        .serviceName(imageName)
        .build();
  }

  public static void main(String[] args) {
    App app = new App(AppProps.builder().outdir("./cdk.out").build());

    // Use BootstraplessSynthesizer() for LocalStack to avoid AWS CDK bootstrapping.
    // This enables local development and testing without deploying resources to AWS.
    StackProps props = StackProps.builder().synthesizer(new BootstraplessSynthesizer()).build();

    new LocalStack(app, "localstack", props);
    app.synth();
    System.out.println("App synthesizing in progress...");
  }

}
