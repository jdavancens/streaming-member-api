#!/bin/bash
set -e
REGION=${AWS_REGION:-us-east-1}
STACK=streaming-member-api-dev

VPC_ID=$(aws ec2 describe-vpcs --filters Name=isDefault,Values=true \
  --query 'Vpcs[0].VpcId' --output text --region $REGION)
SUBNET_IDS=$(aws ec2 describe-subnets --filters Name=vpc-id,Values=$VPC_ID \
  --query 'Subnets[*].SubnetId' --output text --region $REGION | tr '\t' ',')
DEV_IP=$(curl -s https://checkip.amazonaws.com)/32

echo "VPC: $VPC_ID"
echo "Subnets: $SUBNET_IDS"
echo "Dev IP: $DEV_IP"

if [ -z "$DB_PASSWORD" ]; then
  read -s -p "RDS master password: " DB_PASSWORD
  echo
fi

aws cloudformation deploy \
  --template-file scripts/dev-resources.yaml \
  --stack-name $STACK \
  --region $REGION \
  --parameter-overrides \
    VpcId=$VPC_ID \
    "SubnetIds=$SUBNET_IDS" \
    DevMachineIp=$DEV_IP \
    DBPassword=$DB_PASSWORD

echo ""
echo "Outputs (add to .env):"
aws cloudformation describe-stacks \
  --stack-name $STACK \
  --region $REGION \
  --query 'Stacks[0].Outputs[*].[OutputKey,OutputValue]' \
  --output table
