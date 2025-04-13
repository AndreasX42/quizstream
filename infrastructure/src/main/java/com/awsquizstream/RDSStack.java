package com.awsquizstream;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.SubnetSelection;
import software.amazon.awscdk.services.ec2.SubnetType;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.constructs.Construct;
import software.amazon.awscdk.services.ec2.Peer;
import java.util.Collections;

public class RDSStack extends Stack {
	private final DatabaseInstance rdsInstance;
	private final Secret dbSecret;
	private final SecurityGroup rdsSecurityGroup;

	public DatabaseInstance getRdsInstance() {
		return rdsInstance;
	}

	public String getDbSecretArn() {
		return dbSecret.getSecretFullArn();
	}

	public SecurityGroup getRdsSecurityGroup() {
		return rdsSecurityGroup;
	}

	public RDSStack(final Construct scope, final String id, final StackProps props, final Vpc vpc,
			final SecurityGroup bastionSg) {
		super(scope, id, props);

		// create a security group for the RDS instance
		this.rdsSecurityGroup = SecurityGroup.Builder.create(this, "RdsSg")
				.description("SG for RDS")
				.vpc(vpc)
				.allowAllOutbound(false)
				.build();

		rdsSecurityGroup.addEgressRule(
				Peer.anyIpv4(),
				Port.tcp(5432),
				"Allow outbound from 5432");

		rdsSecurityGroup.applyRemovalPolicy(RemovalPolicy.DESTROY);

		rdsSecurityGroup.addIngressRule(
				bastionSg,
				Port.tcp(5432),
				"Allow inbound from Bastion");

		this.dbSecret = Secret.Builder.create(this, "DbSecret")
				.generateSecretString(SecretStringGenerator.builder()
						.secretStringTemplate("{\"username\":\"qsadmin\"}")
						.generateStringKey("password")
						.excludeCharacters("@/\\\"")
						.build())
				.removalPolicy(RemovalPolicy.DESTROY)
				.build();

		// create a RDS instance
		this.rdsInstance = DatabaseInstance.Builder.create(this, "postgres")
				.engine(DatabaseInstanceEngine.postgres(
						PostgresInstanceEngineProps.builder()
								.version(PostgresEngineVersion.VER_16_4)
								.build()))
				.instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MICRO))
				.vpc(vpc)
				.vpcSubnets(SubnetSelection.builder()
						.subnetType(SubnetType.PRIVATE_ISOLATED)
						.build())
				.credentials(Credentials.fromSecret(this.dbSecret))
				.databaseName(CfnStackApp.getRequiredVariable("POSTGRES_DATABASE_NAME"))
				.securityGroups(Collections.singletonList(rdsSecurityGroup))
				.multiAz(false)
				.allocatedStorage(20)
				.maxAllocatedStorage(20)
				.storageEncrypted(true)
				.deletionProtection(false)
				.removalPolicy(RemovalPolicy.DESTROY)
				.build();

		// output the RDS instance endpoint
		CfnOutput.Builder.create(this, "RDSInstanceEndpoint")
				.value(this.rdsInstance.getDbInstanceEndpointAddress())
				.build();
	}
}
