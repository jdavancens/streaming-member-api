import { SQSClient, ReceiveMessageCommand, DeleteMessageCommand } from '@aws-sdk/client-sqs';

const sqs = new SQSClient({ region: process.env.AWS_REGION ?? 'us-east-1' });

export async function poll(queueUrl: string, handler: (body: object) => Promise<void>): Promise<void> {
  while (true) {
    const res = await sqs.send(new ReceiveMessageCommand({
      QueueUrl: queueUrl,
      MaxNumberOfMessages: 10,
      WaitTimeSeconds: 20,
    }));
    for (const msg of res.Messages ?? []) {
      try {
        const body = JSON.parse(msg.Body ?? '{}') as Record<string, unknown>;
        const payload = body.Message ? JSON.parse(body.Message as string) : body;
        await handler(payload);
        await sqs.send(new DeleteMessageCommand({
          QueueUrl: queueUrl,
          ReceiptHandle: msg.ReceiptHandle!,
        }));
      } catch (err) {
        console.error('message processing failed', err);
      }
    }
  }
}
