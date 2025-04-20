package com.awsquizstream;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.elasticloadbalancingv2.*;
import software.constructs.Construct;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.services.ssm.StringParameter;
import software.amazon.awscdk.services.iam.IRole;
import software.amazon.awscdk.services.ecs.Secret;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.elasticloadbalancingv2.Protocol;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.services.elasticloadbalancingv2.HealthCheck;
import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.iam.ManagedPolicy;
import software.amazon.awscdk.services.ssm.IStringParameter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.services.sqs.Queue;

public class ECSFargateStack extends Stack {

	private final FargateTaskDefinition frontendTaskDef;
	private final FargateTaskDefinition backendTaskDef;
	private final FargateService frontendService;
	private final FargateService backendService;
	private final SecurityGroup backendSg;
	private final ISecret dbSecret;

	public ECSFargateStack(
			final Construct scope,
			final String id,
			final StackProps props,
			final IVpc vpc,
			final String dbSecretArn,
			final Queue queue,
			final IApplicationListener albHttpsListener,
			final String albFrontendDomainName,
			final String albBackendDomainName,
			final String albSecurityGroupId,
			final String rdsSecurityGroupId) {
		super(scope, id, props);

		this.dbSecret = setImportedSecret(dbSecretArn);

		// get the db secrets of rds postgres
		Map<String, Secret> dbSecretsMap = getDbSecrets(dbSecret);
		Map<String, Secret> ssmSecretsMap = getSSMParams();

		// create ECS cluster
		Cluster cluster = Cluster.Builder.create(this, "qCluster")
				.vpc(vpc)
				.build();

		cluster.applyRemovalPolicy(RemovalPolicy.DESTROY);

		// security Group for Frontend Service
		SecurityGroup frontendSg = SecurityGroup.Builder.create(this, "AngularSg")
				.vpc(vpc)
				.description("SG for Angular frontend")
				.allowAllOutbound(true)
				.build();

		ISecurityGroup albSecurityGroup = SecurityGroup.fromSecurityGroupId(this, "ImportedAlbSg", albSecurityGroupId);

		frontendSg.addIngressRule(
				albSecurityGroup,
				Port.tcp(80),
				"Allow http inbound from ALB");

		// Frontend Task Definition
		this.frontendTaskDef = FargateTaskDefinition.Builder.create(this, "FrontendTaskDef")
				.memoryLimitMiB(512)
				.cpu(256)
				.build();

		// Get the frontend ecr repo
		String frontendEcrRepoName = CfnStackApp.getRequiredVariable("ECR_REPO_FRONTEND");
		IRepository frontendRepo = Repository.fromRepositoryName(this, "frontend-repo", frontendEcrRepoName);

		// Explicit Log Group for Frontend with DESTROY policy
		LogGroup frontendLogGroup = LogGroup.Builder.create(this, "FrontendLogGroup")
				.removalPolicy(RemovalPolicy.DESTROY)
				.build();

		frontendTaskDef.addContainer("FrontendContainer", ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(frontendRepo))
				.portMappings(List.of(PortMapping.builder().containerPort(80).build()))
				.logging(LogDriver
						.awsLogs(AwsLogDriverProps.builder()
								.logGroup(frontendLogGroup)
								.streamPrefix("angular-").build()))
				.healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder()
						.command(List.of("CMD-SHELL", "curl -f http://localhost/ || exit 1"))
						.interval(Duration.seconds(17))
						.timeout(Duration.seconds(2))
						.retries(3)
						.startPeriod(Duration.seconds(15))
						.build())
				.build());

		// Frontend Fargate Service
		this.frontendService = FargateService.Builder.create(this, "FrontendService")
				.cluster(cluster)
				.taskDefinition(frontendTaskDef)
				.desiredCount(1)
				.assignPublicIp(false)
				.securityGroups(List.of(frontendSg))
				.healthCheckGracePeriod(Duration.seconds(15))
				.vpcSubnets(SubnetSelection.builder()
						.subnetType(SubnetType.PRIVATE_WITH_EGRESS)
						.build())
				.minHealthyPercent(100)
				.build();

		// Add Frontend Target Group to ALB Listener
		albHttpsListener.addTargets("FrontendTg", AddApplicationTargetsProps.builder()
				.port(80)
				.targets(List.of(frontendService))
				.conditions(List.of(ListenerCondition
						.hostHeaders(List.of(albFrontendDomainName))))
				.priority(1)
				.protocol(ApplicationProtocol.HTTP)
				.healthCheck(HealthCheck
						.builder()
						.path("/")
						.port("80")
						.protocol(Protocol.HTTP)
						.interval(Duration.seconds(7))
						.timeout(Duration.seconds(2))
						.healthyThresholdCount(2)
						.unhealthyThresholdCount(5)
						.build())
				.build());

		// --- Backend Service ---

		// Security Group for Backend Service
		this.backendSg = SecurityGroup.Builder.create(this, "BackendSg")
				.vpc(vpc)
				.description("SG for Backend service")
				.allowAllOutbound(true)
				.build();

		backendSg.addIngressRule(
				albSecurityGroup,
				Port.tcp(9090),
				"Allow http inbound from ALB");

		// *** Look up RDS SG by ID and add ingress rule ***
		ISecurityGroup rdsSecurityGroup = SecurityGroup.fromSecurityGroupId(this, "ImportedRdsSg", rdsSecurityGroupId);
		rdsSecurityGroup.addIngressRule(
				backendSg,
				Port.tcp(5432),
				"Allow inbound from Backend");

		// Backend Task Definition
		this.backendTaskDef = FargateTaskDefinition.Builder.create(this, "BackendTaskDef")
				.memoryLimitMiB(1024)
				.cpu(512)
				.build();

		// Get the backend ecr repos
		String springbootEcrRepoName = CfnStackApp.getRequiredVariable("ECR_REPO_SPRINGBOOT");

		IRepository springbootRepo = Repository.fromRepositoryName(this, "springboot-repo",
				springbootEcrRepoName);

		// grant backend task permission to read from secrets manager and ssm parameter
		// store
		grantEcsSqsSendMessageAccess(queue);
		grantEcsDbSecretReadAccess(dbSecret);
		grantEcsSSMReadAccess();

		// Explicit Log Group for SpringBoot with DESTROY policy
		LogGroup springbootLogGroup = LogGroup.Builder.create(this, "SpringbootLogGroup")
				.removalPolicy(RemovalPolicy.DESTROY)
				.build();

		backendTaskDef.addContainer("SpringbootContainer", ContainerDefinitionOptions.builder()
				.image(ContainerImage.fromEcrRepository(springbootRepo))
				.portMappings(List.of(PortMapping.builder().containerPort(9090).build()))
				.logging(LogDriver.awsLogs(
						AwsLogDriverProps.builder()
								.logGroup(springbootLogGroup)
								.streamPrefix("springboot-").build()))
				.healthCheck(software.amazon.awscdk.services.ecs.HealthCheck.builder()
						.command(List.of("CMD-SHELL", "curl -f http://localhost:9090/health || exit 1"))
						.interval(Duration.seconds(17))
						.timeout(Duration.seconds(2))
						.retries(3)
						.startPeriod(Duration.seconds(15))
						.build())
				.secrets(Map.of(
						// From fromSecrets Manager
						"SPRING_DATASOURCE_HOST", dbSecretsMap.get("host"),
						"SPRING_DATASOURCE_PORT", dbSecretsMap.get("port"),
						"SPRING_DATASOURCE_USERNAME", dbSecretsMap.get("username"),
						"SPRING_DATASOURCE_PASSWORD", dbSecretsMap.get("password"),
						"SPRING_DATASOURCE_DATABASE", dbSecretsMap.get("dbname"),
						// From SSM Parameter Store - Use keys matching @Value annotations
						"APP_HOST", ssmSecretsMap.get("appHost"),
						"SPRING_JPA_HIBERNATE_DDL_AUTO", ssmSecretsMap.get("ddlAuto"),
						"SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI", ssmSecretsMap.get("oauth2IssuerUrl"),
						"SPRING_PROFILES_ACTIVE", ssmSecretsMap.get("profilesActive")))
				.build());

		// Backend Fargate Service
		this.backendService = FargateService.Builder.create(this, "BackendService")
				.cluster(cluster)
				.taskDefinition(backendTaskDef)
				.desiredCount(1)
				.assignPublicIp(false)
				.securityGroups(List.of(backendSg))
				.healthCheckGracePeriod(Duration.seconds(30))
				.vpcSubnets(SubnetSelection.builder()
						.subnetType(SubnetType.PRIVATE_WITH_EGRESS)
						.build())
				.minHealthyPercent(100)
				.build();

		// Add Backend Target Group to ALB Listener
		albHttpsListener.addTargets("BackendTG", AddApplicationTargetsProps.builder()
				.port(9090)
				.targets(List.of(backendService))
				.conditions(List.of(ListenerCondition
						.hostHeaders(List.of(albBackendDomainName))))
				.priority(2)
				.protocol(ApplicationProtocol.HTTP)
				.healthCheck(HealthCheck
						.builder()
						.path("/health")
						.port("9090")
						.protocol(Protocol.HTTP)
						.interval(Duration.seconds(7))
						.timeout(Duration.seconds(2))
						.healthyThresholdCount(2)
						.unhealthyThresholdCount(5)
						.build())
				.build());

		this.frontendService.applyRemovalPolicy(RemovalPolicy.DESTROY);
		this.backendService.applyRemovalPolicy(RemovalPolicy.DESTROY);
		this.frontendTaskDef.applyRemovalPolicy(RemovalPolicy.DESTROY);
		this.backendTaskDef.applyRemovalPolicy(RemovalPolicy.DESTROY);

		// outputs
		CfnOutput.Builder.create(this, "qClusterNameOutput")
				.value(cluster.getClusterName())
				.build();

		CfnOutput.Builder.create(this, "qFrontendServiceOutput")
				.value(frontendService.getServiceName())
				.build();

		CfnOutput.Builder.create(this, "qBackendServiceOutput")
				.value(backendService.getServiceName())
				.build();

	}

	public FargateService getFrontendService() {
		return frontendService;
	}

	public FargateService getBackendService() {
		return backendService;
	}

	public IRole getBackendExecutionRole() {
		return backendTaskDef.getExecutionRole();
	}

	public SecurityGroup getBackendSecurityGroup() {
		return backendSg;
	}

	public ISecret setImportedSecret(String dbSecretArn) {
		return software.amazon.awscdk.services.secretsmanager.Secret.fromSecretCompleteArn(this,
				"ImportedDbSecret", dbSecretArn);
	}

	Map<String, Secret> getDbSecrets(ISecret dbSecret) {
		return Map.of(
				"username", Secret.fromSecretsManager(dbSecret, "username"),
				"password", Secret.fromSecretsManager(dbSecret, "password"),
				"host", Secret.fromSecretsManager(dbSecret, "host"),
				"port", Secret.fromSecretsManager(dbSecret, "port"),
				"dbname", Secret.fromSecretsManager(dbSecret, "dbname"));
	}

	private IStringParameter param(String id, String name) {
		return StringParameter.fromStringParameterName(this, id, name);
	}

	Map<String, Secret> getSSMParams() {

		return Map.of(
				"appHost", Secret.fromSsmParameter(param("AppHostParam", "/api/APP_HOST")),
				"ddlAuto", Secret.fromSsmParameter(param("DdlAutoParam", "/api/SPRING_JPA_HIBERNATE_DDL_AUTO")),
				"profilesActive", Secret.fromSsmParameter(param("ProfilesActiveParam", "/api/SPRING_PROFILES_ACTIVE")),
				"oauth2IssuerUrl", Secret.fromSsmParameter(
						param("Oauth2IssuerUrlParam", "/cognito/COGNITO_USER_POOL_ISSUER_URL")));
	}

	public void grantEcsDbSecretReadAccess(ISecret dbSecret) {
		if (this.backendTaskDef != null && this.backendTaskDef.getExecutionRole() != null) {
			dbSecret.grantRead(this.backendTaskDef.getExecutionRole());
		}
	}

	public void grantEcsSSMReadAccess() {
		if (this.backendTaskDef != null && this.backendTaskDef.getExecutionRole() != null) {
			this.backendTaskDef.getExecutionRole().addManagedPolicy(
					ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMReadOnlyAccess"));
		}
	}

	public void grantEcsSqsSendMessageAccess(Queue queue) {
		if (this.backendTaskDef != null && this.backendTaskDef.getExecutionRole() != null) {
			queue.grantSendMessages(this.backendTaskDef.getExecutionRole());
		}
	}

}