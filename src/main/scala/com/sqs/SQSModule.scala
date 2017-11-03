package com.sqs

import java.util.concurrent.Future

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.handlers.AsyncHandler
import com.amazonaws.services.sqs.model._
import com.amazonaws.services.sqs.{AmazonSQSAsyncClient, AmazonSQSClient}
import com.typesafe.config.{Config, ConfigFactory}

/**
  * Created by SeungEun on 2016/12/18.
  */
trait Module {
  val config: Config = ConfigFactory.load(this.getClass().getClassLoader(), "application.conf")
  val sqs: AmazonSQSClient
  val queueUrl: String
  
  def sqsClient: AmazonSQSClient
  
  def createQueueUrl: String
  
  def getMessages:java.util.List[Message]
  
  def deleteMessage(message: Message) = {
    val receiptHandle = message.getReceiptHandle
    //キュー削除
    println("Deleting the test queue.\n")
    sqs.deleteMessage(queueUrl, receiptHandle)
  }
}

trait BaseSQS extends Module {
  lazy val sqs: AmazonSQSClient = sqsClient
  val queueUrl = createQueueUrl
  
  def sqsClient: AmazonSQSClient
  
  def createQueueUrl: String = {
    //キューを作成
    println("Creating a new SQS queue called MyQueue")
    sqs.createQueue(new CreateQueueRequest("MyQueue")).getQueueUrl()
  }
}

trait BaseSQSAsync extends BaseSQS {
  lazy override val sqs: AmazonSQSAsyncClient = sqsClient
  override def sqsClient: AmazonSQSAsyncClient
  override def createQueueUrl: String = {
    //キューを作成
    println("Creating a new SQS queue called MyQueue")
    println(sqs)
    sqs.createQueue(new CreateQueueRequest("MyQueue")).getQueueUrl()
  }
}

object SQSModule extends BaseSQS {
  
  private def credentials = new BasicAWSCredentials(config.getString("sqs.accessKey"), config.getString("sqs.secretKey"))
  
  override def sqsClient = {
    //SQSオブジェクト作成
    println("create AmazonSQSClient ")
    new AmazonSQSClient(credentials)
  }
  
  def sendMessage(messageBody: String) = {
    sqs.sendMessage(new SendMessageRequest(queueUrl, messageBody))
  }
  
  def getMessages:java.util.List[Message] = {
    // メッセージ受信. withMaxNumberOfMessagesで受信するメッセージ数を指定
    println("Receiving messages from MyQueue")
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
  
  override def sqsClient: AmazonSQSAsyncClient = {
    new AmazonSQSAsyncClient(credentials)
  }
  
  def sendMessageAsync(messageBody: String):Future[SendMessageResult] = {
    sqs.sendMessageAsync(new SendMessageRequest(queueUrl, messageBody), new AsyncHandler[SendMessageRequest, SendMessageResult] {
      override def onSuccess(request: SendMessageRequest, result: SendMessageResult) = {
        println("Success!! " + result.getMessageId())
      }
      
      override def onError(exception: Exception) {
        println("Failer!!!")
        exception.printStackTrace()
      }
    })
  }
  
  def getMessages:java.util.List[Message] = {
    // メッセージ受信. withMaxNumberOfMessagesで受信するメッセージ数を指定
    println("Receiving messages from MyQueue")
    val request = new ReceiveMessageRequest(queueUrl) withMaxNumberOfMessages (10)
    // 他のReceiverが受信できなくなる時間
    request.setVisibilityTimeout(10)
    // Long pollの設定
    request.setWaitTimeSeconds(20)
    
    sqs.receiveMessage(request).getMessages()
  }
  
}
