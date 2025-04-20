package com.awsquizstream;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class QuizstreamStack extends Stack {

	public QuizstreamStack(final Construct scope, final String id) {
		this(scope, id, null);
	}

	public QuizstreamStack(final Construct scope, final String id, final StackProps props) {
		super(scope, id, props);

		// Ensure all nested stacks inherit the environment
		StackProps nestedStackProps = StackProps.builder().env(props.getEnv()).build();

		// create KMS customer managed key
		// KMSStack kmsStack = new KMSStack(this, "kms", nestedStackProps);

		// create VPC
		VPCStack vpcStack = new VPCStack(this, "Vpc", nestedStackProps);

		// create Bastion host for database access
		BastionStack bastionStack = new BastionStack(this, "Bastion", nestedStackProps, vpcStack.getVpc());

		// create postgres RDS instance
		RDSStack rdsStack = new RDSStack(this, "Rds", nestedStackProps, vpcStack.getVpc(),
				bastionStack.getBastionSg());

		// // create application load balancer
		ALBStack albStack = new ALBStack(this, "Alb", nestedStackProps,
				vpcStack.getVpc());

		// create cognito user pool and client
		CognitoStack cognitoStack = new CognitoStack(this, "Cognito", nestedStackProps);

		// create SQS queue and lambda function
		SqsLambdaStack sqsLambdaStack = new SqsLambdaStack(this, "SqsLambdaStack", nestedStackProps,
				rdsStack.getDbSecretArn());

		// // create ECS Fargate cluster and services
		ECSFargateStack ecsFargateStack = new ECSFargateStack(this, "EcsFargate",
				nestedStackProps,
				vpcStack.getVpc(),
				rdsStack.getDbSecretArn(),
				sqsLambdaStack.getQueue(),
				albStack.getHttpsListener(),
				albStack.getFrontendDomainName(),
				albStack.getBackendDomainName(),
				albStack.getAlbSecurityGroup().getSecurityGroupId(),
				rdsStack.getRdsSecurityGroup().getSecurityGroupId());

		// create Angular CodePipeline
		FrontendCodePipelineStack frontendCodePipelineStack = new FrontendCodePipelineStack(this,
				"FrontendPipeline",
				nestedStackProps,
				ecsFargateStack.getFrontendService());

		// create Backend CodePipeline
		BackendCodePipelineStack backendCodePipelineStack = new BackendCodePipelineStack(this,
				"BackendPipeline",
				nestedStackProps,
				ecsFargateStack.getBackendService());
	}
}
