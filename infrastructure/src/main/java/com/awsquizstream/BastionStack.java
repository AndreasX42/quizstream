package com.awsquizstream;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.constructs.Construct;

import java.util.Collections;
import software.amazon.awscdk.RemovalPolicy;

public class BastionStack extends Stack {
	private final SecurityGroup bastionSg;
	private final Instance bastion;

	public SecurityGroup getBastionSg() {
		return bastionSg;
	}

	public Instance getBastion() {
		return bastion;
	}

	public BastionStack(final Construct scope, final String id, final StackProps props, final Vpc vpc) {
		super(scope, id, props);

		this.bastionSg = SecurityGroup.Builder.create(this, "BastionSg")
				.vpc(vpc)
				.description("SG for Bastion host")
				.allowAllOutbound(true)
				.build();

		Role ssmRole = Role.Builder.create(this, "BastionSSMRole")
				.assumedBy(new ServicePrincipal("ec2.amazonaws.com"))
				.managedPolicies(Collections.singletonList(
						ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMManagedInstanceCore")))
				.build();

		this.bastion = Instance.Builder.create(this, "host")
				.vpc(vpc)
				.instanceType(InstanceType.of(InstanceClass.T3, InstanceSize.MICRO))
				.machineImage(MachineImage.latestAmazonLinux2023())
				.securityGroup(this.bastionSg)
				.vpcSubnets(SubnetSelection.builder()
						.subnetType(SubnetType.PUBLIC)
						.build())
				.role(ssmRole)
				.build();

		bastionSg.applyRemovalPolicy(RemovalPolicy.DESTROY);
		bastion.applyRemovalPolicy(RemovalPolicy.DESTROY);
		ssmRole.applyRemovalPolicy(RemovalPolicy.DESTROY);

		// output the public DNS
		CfnOutput.Builder.create(this, "BastionHostPublicDns")
				.value(this.bastion.getInstancePublicDnsName())
				.build();
	}
}
