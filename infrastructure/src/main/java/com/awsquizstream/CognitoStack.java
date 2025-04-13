package com.awsquizstream;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.services.cognito.*;
import software.constructs.Construct;

import software.amazon.awscdk.CfnOutput;
import software.amazon.awscdk.services.ssm.StringParameter;

import java.util.Arrays;
import software.amazon.awscdk.Duration;

public class CognitoStack extends Stack {

    private final IUserPool userPool;
    private final IUserPoolClient spaClient;

    public CognitoStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        String existingUserPoolArn = CfnStackApp.getRequiredVariable("COGNITO_USER_POOL_ARN");
        boolean userPoolExists = existingUserPoolArn != null && !existingUserPoolArn.isEmpty();

        if (userPoolExists) {
            this.userPool = UserPool.fromUserPoolArn(this, "qUserPool", existingUserPoolArn);
        } else {
            this.userPool = UserPool.Builder.create(this, "qUserPool")
                    .userPoolName("qUserPool")
                    .selfSignUpEnabled(true)
                    .signInAliases(SignInAliases.builder().email(true).username(true).build())
                    .autoVerify(AutoVerifiedAttrs.builder().email(true).build())
                    .standardAttributes(StandardAttributes.builder()
                            .email(StandardAttribute.builder().required(true).mutable(false)
                                    .build())
                            .build())
                    .passwordPolicy(PasswordPolicy.builder()
                            .minLength(6)
                            // .requireLowercase(true)
                            // .requireDigits(true)
                            // .requireSymbols(true)
                            // .requireUppercase(true)
                            .tempPasswordValidity(Duration.days(1))
                            .build())
                    .accountRecovery(AccountRecovery.EMAIL_ONLY)
                    .removalPolicy(RemovalPolicy.RETAIN)
                    .build();
        }

        // ---- User Pool Client ----
        String existingUserPoolClientId = CfnStackApp.getRequiredVariable("COGNITO_USER_POOL_CLIENT_ID");
        boolean userPoolClientExists = existingUserPoolClientId != null && !existingUserPoolClientId.isEmpty();

        if (userPoolExists && userPoolClientExists) {
            this.spaClient = UserPoolClient.fromUserPoolClientId(this, "qSpaClient",
                    existingUserPoolClientId);
        } else {
            String frontendDomainName = CfnStackApp.getRequiredVariable("FRONTEND_DOMAIN_NAME");
            String callbackUrl = "https://" + frontendDomainName;

            this.spaClient = UserPoolClient.Builder.create(this, "qSpaClient")
                    .userPool(this.userPool)
                    .userPoolClientName("qSpaClient")
                    .generateSecret(false)
                    .enableTokenRevocation(true)
                    .refreshTokenValidity(Duration.hours(2))
                    .accessTokenValidity(Duration.minutes(30))
                    .authFlows(AuthFlow.builder()
                            .userSrp(true)
                            .build())
                    .oAuth(OAuthSettings.builder()
                            .flows(OAuthFlows.builder()
                                    .authorizationCodeGrant(true)
                                    .build())
                            .scopes(Arrays.asList(
                                    OAuthScope.OPENID,
                                    OAuthScope.EMAIL,
                                    OAuthScope.PROFILE))
                            .callbackUrls(Arrays.asList(callbackUrl))
                            .logoutUrls(Arrays.asList(callbackUrl))
                            .build())
                    .supportedIdentityProviders(
                            Arrays.asList(UserPoolClientIdentityProvider.COGNITO))
                    .preventUserExistenceErrors(true)
                    .build();

            this.spaClient.applyRemovalPolicy(RemovalPolicy.RETAIN);
        }

        // --- Outputs ---

        CfnOutput.Builder.create(this, "qUserPoolIdOutput")
                .value(userPool.getUserPoolId())
                .description("Cognito User Pool ID")
                .exportName("qUserPoolId")
                .build();

        CfnOutput.Builder.create(this, "qSpaUserPoolClientIdOutput")
                .value(this.spaClient.getUserPoolClientId())
                .description("Cognito User Pool Client ID for SPA")
                .exportName("qSpaUserPoolClientId")
                .build();

        CfnOutput.Builder.create(this, "qUserPoolArnOutput")
                .value(userPool.getUserPoolArn())
                .description("Cognito User Pool ARN")
                .exportName("qUserPoolArn")
                .build();

        // Output Cognito Issuer URL
        // Format: https://cognito-idp.{region}.amazonaws.com/{userPoolId}
        String issuerUrl = String.format("https://cognito-idp.%s.amazonaws.com/%s",
                this.getRegion(), userPool.getUserPoolId());

        CfnOutput.Builder.create(this, "qUserPoolIssuerUrlOutput")
                .value(issuerUrl)
                .description("Cognito User Pool Issuer URL")
                .exportName("qUserPoolIssuerUrl")
                .build();

        // --- Store IDs in SSM Parameter Store ---

        StringParameter.Builder.create(this, "UserPoolIdParam")
                .parameterName("/cognito/COGNITO_USER_POOL_ID")
                .stringValue(userPool.getUserPoolId())
                .description("Cognito User Pool ID for QuizStream")
                .build();

        StringParameter.Builder.create(this, "UserPoolClientIdParam")
                .parameterName("/cognito/COGNITO_USER_POOL_CLIENT_ID")
                .stringValue(spaClient.getUserPoolClientId())
                .description("Cognito User Pool Client ID for QuizStream SPA")
                .build();

        StringParameter.Builder.create(this, "UserPoolIssuerUrlParam")
                .parameterName("/cognito/COGNITO_USER_POOL_ISSUER_URL")
                .stringValue(issuerUrl)
                .description("Cognito User Pool Issuer URL for QuizStream")
                .build();

    }

    // Getter for User Pool
    public IUserPool getUserPool() {
        return userPool;
    }

    // Getter for SPA Client (Optional, but good practice)
    public IUserPoolClient getSpaClient() {
        return spaClient;
    }
}