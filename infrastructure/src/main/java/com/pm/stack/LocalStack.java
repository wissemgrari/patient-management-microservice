package com.pm.stack;

import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Token;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

public class LocalStack extends Stack {

  private final Vpc vpc;

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

}
