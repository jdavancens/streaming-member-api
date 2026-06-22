import { SNSClient, PublishCommand } from '@aws-sdk/client-sns';

const sns = new SNSClient({ region: process.env.AWS_REGION ?? 'us-east-1' });

export async function publish(topicArn: string, message: object): Promise<void> {
  await sns.send(new PublishCommand({
    TopicArn: topicArn,
    Message: JSON.stringify(message),
  }));
}
