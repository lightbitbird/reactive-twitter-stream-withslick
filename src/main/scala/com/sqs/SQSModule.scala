package com.sqs

import java.util.concurrent.Future

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.{AmazonSQSAsyncClient, AmazonSQSClient}
import com.typesafe.config.{Config, ConfigFactory}
import org.slf4j.LoggerFactory

/**
  * Created by SeungEun on 2016/12/18.
  */
trait Module {
  protected val log = LoggerFactory.getLogger(this.getClass.getSimpleName)

  protected val config: Config = ConfigFactory.load(this.getClass().getClassLoader(), "application.conf")
  protected val sqs: AmazonSQSClient
  protected val queueUrl: String

  protected def sqsClient: AmazonSQSClient

  protected def createQueueUrl: String

  def getMessages: java.util.List[Message]

  def deleteMessage(message: Message) = {
    val receiptHandle = message.getReceiptHandle

    log.info("Deleting the queue.")
    sqs.deleteMessage(queueUrl, receiptHandle)
  }
}

trait BaseSQS extends Module {
  protected lazy val sqs: AmazonSQSClient = sqsClient
  protected val queueUrl = createQueueUrl

  protected def sqsClient: AmazonSQSClient

  protected def createQueueUrl: String = {
    sqs.createQueue(new CreateQueueRequest("MyQueue")).getQueueUrl()
  }
}

trait BaseSQSAsync extends BaseSQS {
  protected lazy override val sqs: AmazonSQSAsyncClient = sqsClient

  protected override def sqsClient: AmazonSQSAsyncClient

  protected override def createQueueUrl: String = {
    sqs.createQueue(new CreateQueueRequest("MyQueue")).getQueueUrl()
  }
}

object SQSModule extends BaseSQS {

  private def credentials = new BasicAWSCredentials(config.getString("sqs.accessKey"), config.getString("sqs.secretKey"))

  protected override def sqsClient = {
    //SQSオブジェクト作成
    log.info("create AmazonSQSClient ")
    new AmazonSQSClient(credentials)
  }

  def sendMessage(messageBody: String) = {
    sqs.sendMessage(new SendMessageRequest(queueUrl, messageBody))
  }

  def getMessages: java.util.List[Message] = {
    // メッセージ受信. withMaxNumberOfMessagesで受信するメッセージ数を指定
    log.info("Receiving messages from MyQueue")
    val request = new ReceiveMessageRequest(queueUrl) withMaxNumberOfMessages (10)
    // 他のReceiverが受信できなくなる時間
    request.setVisibilityTimeout(10)
    // Long pollの設定
    request.setWaitTimeSeconds(20)

    sqs.receiveMessage(request).getMessages()
  }

}

object SQSModuleAsync extends BaseSQSAsync {

  private def credentials = new BasicAWSCredentials(config.getString("sqs.accessKey"), config.getString("sqs.secretKey"))

  protected override def sqsClient: AmazonSQSAsyncClient = {
    new AmazonSQSAsyncClient(credentials)
  }

  def sendMessageAsync(messageBody: String): Future[SendMessageResult] = {
    sqs.sendMessageAsync(new SendMessageRequest(queueUrl, messageBody), new AsyncHandler[SendMessageRequest, SendMessageResult] {
      override def onSuccess(request: SendMessageRequest, result: SendMessageResult) = {
        log.info("Success!! " + result.getMessageId())
      }

      override def onError(exception: Exception) {
        log.error("Failer!!!")
        exception.printStackTrace()
      }
    })
  }

  def getMessages: java.util.List[Message] = {
    // メッセージ受信. withMaxNumberOfMessagesで受信するメッセージ数を指定
    log.info("Receiving messages from MyQueue")
    val request = new ReceiveMessageRequest(queueUrl) withMaxNumberOfMessages (10)
    // 他のReceiverが受信できなくなる時間
    request.setVisibilityTimeout(10)
    // Long pollの設定
    request.setWaitTimeSeconds(20)

    sqs.receiveMessage(request).getMessages()
  }

}
