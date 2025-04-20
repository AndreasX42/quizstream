package com.awsquizstream;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.Stack;
import software.constructs.Construct;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.LayerVersion;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.sqs.Queue;
import java.util.List;
import java.util.Map;
import software.amazon.awscdk.services.secretsmanager.ISecret;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.iam.ManagedPolicy;

public class SqsLambdaStack extends Stack {

    private final Queue queue;

    public Queue getQueue() {
        return queue;
    }

    public ISecret getImportedSecret(String dbSecretArn) {
        return software.amazon.awscdk.services.secretsmanager.Secret.fromSecretCompleteArn(this,
                "ImportedDbSecret", dbSecretArn);
    }

    public SqsLambdaStack(final Construct scope, final String id, final StackProps props,
            final String dbSecretArn) {
        super(scope, id, props);

        // define the queue
        this.queue = Queue.Builder.create(this, "QuizJobsQueue")
                .queueName("quiz-jobs-queue.fifo")
                .visibilityTimeout(Duration.seconds(300))
                .fifo(true)
                .build();

        // define the Lambda Layer
        LayerVersion layer = LayerVersion.Builder.create(this, "QuizLayer")
                .layerVersionName("quiz-requirements-layer")
                .compatibleRuntimes(List.of(Runtime.PYTHON_3_11))
                .code(Code.fromAsset("resources/layers/quiz_requirements_layer.zip"))
                .build();

        // define the Lambda Function
        Function fn = Function.Builder.create(this, "QuizSqsLambda")
                .runtime(Runtime.PYTHON_3_11)
                .handler("quiz_generator.lambda_function.lambda_handler")
                .code(Code.fromAsset("resources/lambda/quiz_generator_zip.zip"))
                .memorySize(256)
                .timeout(Duration.seconds(180))
                .layers(List.of(layer))
                .environment(Map.of(
                        "QUEUE_URL", queue.getQueueUrl(),
                        "DB_SECRET_ARN", dbSecretArn))
                .build();

        // Grant sqs permissions
        queue.grantConsumeMessages(fn);

        // Grant secret read permissions
        getImportedSecret(dbSecretArn).grantRead(fn);

        // Attach the AmazonSSMReadOnlyAccess managed policy
        fn.getRole().addManagedPolicy(
                ManagedPolicy.fromAwsManagedPolicyName("AmazonSSMReadOnlyAccess"));

        // Add SQS trigger
        fn.addEventSource(new SqsEventSource(queue));
    }
}
